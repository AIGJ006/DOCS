package com.team.blog.interaction.application;

import com.team.blog.interaction.infra.RedisViewStore;
import com.team.blog.interaction.infra.ViewDailyRepository;
import com.team.blog.interaction.infra.ViewStoreUnavailableException;
import com.team.blog.post.application.PostCounterService;
import com.team.blog.post.application.exception.AutosaveUnavailableException;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 조회수 1분 반영 (009 T032, FR-027·028·030, contracts/view-pipeline.md §3, research R6). 순서:
 *
 * <ol>
 *   <li>이전 실행이 남긴 처리 중 묶음({@code view:processing:*})을 먼저 마저 반영한다.
 *   <li>모음 묶음({@code view:pending:*})을 처리 중 묶음으로 옮기고(원자적 RENAME) 반영한다.
 * </ol>
 *
 * 글 하나씩 트랜잭션으로 {@code post.view_count}와 {@code post_view_daily}(묶음 키의 날짜)를 함께 더하고, 커밋한 뒤에야 묶음에서 그
 * 글을 지운다(002 규칙: 트랜잭션 안 Redis 삭제 금지). 그래서 중간에 멈춰도 다음 실행이 남은 글만 반영하고 두 번 더하지 않는다. 완전 삭제된 글은 건너뛰고
 * 묶음에서 지운다. Redis 장애면 그 회차를 끝낸다(묶음은 남는다). 여러 서버에서 한 곳만 돌도록 ShedLock {@code view-flush}.
 */
@Component
public class ViewFlushJob {

    private static final Logger log = LoggerFactory.getLogger(ViewFlushJob.class);

    /** 한 회차 결과: 반영한 묶음 수, 반영한 글 수, 건너뛴 글 수(삭제된 글·잘못된 항목). */
    public record Result(int batches, int applied, int skipped) {}

    private final RedisViewStore store;
    private final PostCounterService counters;
    private final ViewDailyRepository daily;
    private final TransactionTemplate tx;

    public ViewFlushJob(
            RedisViewStore store,
            PostCounterService counters,
            ViewDailyRepository daily,
            TransactionTemplate tx) {
        this.store = store;
        this.counters = counters;
        this.daily = daily;
        this.tx = tx;
    }

    @Scheduled(
            fixedDelayString = "${blog.view.flush-interval}",
            initialDelayString = "${blog.view.flush-interval}")
    @SchedulerLock(name = "view-flush", lockAtMostFor = "PT5M")
    public void run() {
        Result result = flush();
        if (result.batches() > 0) {
            log.info(
                    "조회수 1분 반영: 묶음 {}개, 반영 {}건, 건너뜀 {}건",
                    result.batches(),
                    result.applied(),
                    result.skipped());
        }
    }

    /** 한 회차를 돈다(시험에서 직접 부른다). Redis 장애면 그때까지의 결과를 돌려준다. */
    public Result flush() {
        Counter counter = new Counter();
        try {
            for (String key : store.scan(RedisViewStore.PROCESSING_PREFIX + "*")) {
                drain(key, counter);
            }
            for (String key : store.scan(RedisViewStore.PENDING_PREFIX + "*")) {
                Optional<String> processing = store.claim(key);
                if (processing.isPresent()) {
                    drain(processing.get(), counter);
                }
            }
        } catch (ViewStoreUnavailableException | AutosaveUnavailableException e) {
            log.warn("Redis 장애로 조회수 반영을 이번 회차에 멈춥니다. 남은 묶음은 다음 회차에 반영합니다");
        }
        return new Result(counter.batches, counter.applied, counter.skipped);
    }

    private void drain(String key, Counter counter) {
        Optional<LocalDate> date = RedisViewStore.dateOf(key);
        if (date.isEmpty()) {
            return;
        }
        counter.batches++;
        for (Map.Entry<Long, Long> entry : store.entries(key).entrySet()) {
            long postId = entry.getKey();
            long n = entry.getValue();
            boolean applied = n > 0 && apply(postId, date.get(), n);
            if (applied) {
                counter.applied++;
            } else {
                counter.skipped++;
            }
            // 커밋 뒤에만 지운다
            store.remove(key, postId);
        }
    }

    private boolean apply(long postId, LocalDate date, long n) {
        try {
            Boolean done =
                    tx.execute(
                            status -> {
                                if (!counters.addViews(postId, n)) {
                                    return false;
                                }
                                daily.upsert(postId, date, n);
                                return true;
                            });
            return Boolean.TRUE.equals(done);
        } catch (DataIntegrityViolationException e) {
            // 반영 도중 글이 완전 삭제됨
            return false;
        }
    }

    private static final class Counter {
        int batches;
        int applied;
        int skipped;
    }
}
