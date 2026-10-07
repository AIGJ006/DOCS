package com.team.blog.post.application;

import com.team.blog.post.infra.AutosaveEntry;
import com.team.blog.post.infra.PostEditRepository;
import com.team.blog.post.infra.PostEditRepository.FlushTarget;
import com.team.blog.post.infra.RedisAutosaveStore;
import com.team.blog.shared.infra.redis.RedisGuard;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 1분 반영 (002 T082, FR-007 ③, EV §3, A-5). {@code autosave:dirty}의 글마다 Redis 보관분을 버전 조건으로 DB에 반영한다 —
 * 임시글은 {@code post}, 발행 글은 {@code post_draft}. 휴지통 글도 반영한다(B-3 ⑥). 반영 후 Redis 키는 지우지 않고(24시간 TTL),
 * 키 버전이 반영한 버전과 같을 때만 dirty에서 뺀다. 글마다 예외를 격리하고, Redis 장애면 그 회차를 건너뛴다. 여러 서버에서 한 곳만 돌도록 ShedLock
 * {@code autosave-flush}.
 */
@Component
public class AutosaveFlushJob {

    private static final Logger log = LoggerFactory.getLogger(AutosaveFlushJob.class);

    /** PostgreSQL 외래 키 위반 — 반영 중에 글이 완전 삭제됨. */
    private static final String FOREIGN_KEY_VIOLATION = "23503";

    private final RedisGuard redisGuard;
    private final RedisAutosaveStore store;
    private final PostEditRepository edits;
    private final AutosaveService autosaveService;
    private final SavedContentImages images;
    private final TransactionTemplate tx;

    public AutosaveFlushJob(
            RedisGuard redisGuard,
            RedisAutosaveStore store,
            PostEditRepository edits,
            AutosaveService autosaveService,
            SavedContentImages images,
            TransactionTemplate tx) {
        this.redisGuard = redisGuard;
        this.store = store;
        this.edits = edits;
        this.autosaveService = autosaveService;
        this.images = images;
        this.tx = tx;
    }

    @Scheduled(
            fixedDelayString = "${blog.autosave.flush-interval}",
            initialDelayString = "${blog.autosave.flush-interval}")
    @SchedulerLock(name = "autosave-flush", lockAtMostFor = "50s")
    public void flush() {
        if (!redisGuard.isAvailable()) {
            log.warn("Redis 장애로 자동 저장 1분 반영을 이번 회차에 건너뜁니다");
            return;
        }
        Set<Long> postIds = store.dirtyPostIds();
        int done = 0;
        for (long postId : postIds) {
            try {
                flushOne(postId);
                done++;
            } catch (RuntimeException e) {
                log.warn(
                        "자동 저장 반영 실패, 다음 회차에 다시 시도합니다: postId={} {}",
                        postId,
                        e.getClass().getSimpleName());
            }
        }
        if (!postIds.isEmpty()) {
            log.info("자동 저장 1분 반영: 대상 {}건, 처리 {}건", postIds.size(), done);
        }
    }

    private void flushOne(long postId) {
        Optional<AutosaveEntry> found = store.find(postId);
        if (found.isEmpty()) {
            // 키가 만료됨: dirty 항목만 정리
            store.clearDirtyIfVersion(postId, 0);
            return;
        }
        AutosaveEntry entry = found.get();
        Optional<FlushTarget> target = edits.findFlushTarget(postId);
        if (target.isEmpty()) {
            store.delete(postId);
            return;
        }
        List<String> keys = images.ownedKeys(entry.contentMd(), target.get().authorId());
        try {
            tx.executeWithoutResult(
                    status -> {
                        autosaveService.apply(
                                target.get().status(),
                                postId,
                                entry.title(),
                                entry.contentMd(),
                                entry.version(),
                                AutosaveService.savedAtOf(entry));
                        images.attach(postId, target.get().authorId(), keys);
                    });
        } catch (DataIntegrityViolationException e) {
            if (isForeignKeyViolation(e)) {
                store.delete(postId);
                return;
            }
            throw e;
        }
        store.clearDirtyIfVersion(postId, entry.version());
    }

    private static boolean isForeignKeyViolation(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && FOREIGN_KEY_VIOLATION.equals(sql.getSQLState())) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }
}
