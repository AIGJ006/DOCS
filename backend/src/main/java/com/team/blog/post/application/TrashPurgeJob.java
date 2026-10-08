package com.team.blog.post.application;

import com.team.blog.post.domain.TrashablePost;
import com.team.blog.post.infra.TrashPostRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 휴지통 비우기 배치 (006 T062, US4-3, FR-030, contracts/events.md §3, research R13). 매일 {@code
 * blog.post.trash.purge-cron}(기본 03:30, 서비스 시간대)에 보관 기간(기본 30일)이 지난 휴지통 글을 완전히 지운다.
 *
 * <ul>
 *   <li>서버가 여러 대여도 ShedLock({@code trashPurgeJob})으로 한 번만 돈다.
 *   <li>대상을 오래된 순으로 {@code purge-batch-size}개씩 고르고, 글마다 새 트랜잭션에서 잠근 뒤 다시 확인한다 — 그사이 복구됐거나 이미 지워졌으면
 *       건너뛴다. 지우는 일은 {@link PostPurgeService}(신고·사진 단계 → DELETE → {@code PostPurged})가 한다.
 *   <li>한 글이 실패하면(FK 위반 23001·23503 포함) 그 글만 롤백하고 ERROR 로그를 남긴 뒤 다음 글로 간다. 실패한 글은 같은 실행에서 다시 고르지 않고
 *       다음 날 다시 시도한다.
 *   <li>대상이 없거나 {@code purge-max-duration}(기본 30분)에 이르면 다음 묶음을 시작하지 않는다.
 *   <li>끝에 처리·건너뜀·실패 수와 소요 시간을 INFO 로그로 남긴다.
 * </ul>
 */
@Component
public class TrashPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(TrashPurgeJob.class);

    /**
     * 한 번 실행한 결과.
     *
     * @param purged 지운 글 수
     * @param skipped 잠근 뒤 다시 보니 대상이 아니어서 건너뛴 글 수
     * @param failed 실패해 남은 글 수
     * @param batches 처리한 묶음 수
     * @param elapsed 소요 시간
     * @param timedOut 최대 실행 시간에 이르러 남은 묶음을 미뤘는지
     */
    public record Result(
            int purged, int skipped, int failed, int batches, Duration elapsed, boolean timedOut) {}

    private enum Outcome {
        PURGED,
        SKIPPED
    }

    private final TrashPostRepository trashPosts;
    private final PostPurgeService purgeService;
    private final TrashProperties properties;
    private final Clock clock;
    private final TransactionTemplate perPost;

    public TrashPurgeJob(
            TrashPostRepository trashPosts,
            PostPurgeService purgeService,
            TrashProperties properties,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.trashPosts = trashPosts;
        this.purgeService = purgeService;
        this.properties = properties;
        this.clock = clock;
        this.perPost = new TransactionTemplate(transactionManager);
        this.perPost.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Scheduled(cron = "${blog.post.trash.purge-cron}", zone = "${blog.time-zone}")
    @SchedulerLock(name = "trashPurgeJob", lockAtMostFor = "PT40M")
    public void run() {
        purgeExpired(clock.instant(), properties.purgeMaxDuration());
    }

    /**
     * {@code now - retention}보다 먼저 휴지통에 들어간 글을 지운다. 잠금 없이 부르는 것은 테스트·운영 도구뿐이다.
     *
     * @param now 기준 시각
     * @param maxDuration 이 시간이 지나면 다음 묶음을 시작하지 않는다 (진행 중인 묶음은 끝낸다)
     */
    public Result purgeExpired(Instant now, Duration maxDuration) {
        long started = System.nanoTime();
        Instant cutoff = now.minus(properties.retention());
        int batchSize = properties.purgeBatchSize();
        Set<Long> failedIds = new HashSet<>();
        int purged = 0;
        int skipped = 0;
        int batches = 0;
        boolean timedOut = false;
        for (; ; ) {
            List<Long> ids = trashPosts.findExpiredIds(cutoff, batchSize, failedIds);
            if (ids.isEmpty()) {
                break;
            }
            batches++;
            for (long id : ids) {
                try {
                    Outcome outcome = perPost.execute(status -> purgeOne(id, cutoff));
                    if (outcome == Outcome.PURGED) {
                        purged++;
                    } else {
                        skipped++;
                    }
                } catch (RuntimeException e) {
                    failedIds.add(id);
                    log.error("휴지통 비우기 실패: postId={}", id, e);
                }
            }
            if (ids.size() < batchSize) {
                break;
            }
            if (Duration.ofNanos(System.nanoTime() - started).compareTo(maxDuration) >= 0) {
                timedOut = true;
                break;
            }
        }
        Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
        log.info(
                "휴지통 비우기 완료: purged={} skipped={} failed={} batches={} elapsedMs={}"
                        + " timedOut={}",
                purged,
                skipped,
                failedIds.size(),
                batches,
                elapsed.toMillis(),
                timedOut);
        return new Result(purged, skipped, failedIds.size(), batches, elapsed, timedOut);
    }

    private Outcome purgeOne(long id, Instant cutoff) {
        Optional<TrashablePost> locked = trashPosts.lockById(id);
        if (locked.isEmpty()
                || !locked.get().isTrashed()
                || !locked.get().deletedAt().isBefore(cutoff)) {
            log.info("휴지통 비우기 건너뜀: postId={}", id);
            return Outcome.SKIPPED;
        }
        purgeService.purge(id, locked.get().authorId(), true);
        return Outcome.PURGED;
    }
}
