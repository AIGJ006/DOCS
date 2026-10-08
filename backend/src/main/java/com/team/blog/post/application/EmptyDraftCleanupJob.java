package com.team.blog.post.application;

import com.team.blog.post.config.PostAuthoringProperties;
import com.team.blog.post.infra.PostMaintenanceRepository;
import com.team.blog.post.infra.RedisAutosaveStore;
import com.team.blog.shared.infra.redis.RedisGuard;
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 빈 임시글 정리 (002 T117, FR-050, US7 #2, B-4·B-9, EV §3). 매일 {@code blog.cleanup.empty-draft-cron}(기본
 * 03:30, {@code blog.cleanup.zone})에 만든 지도 고친 지도 {@code empty-draft-age}(24시간)가 지난 빈 임시글을 휴지통을 거치지
 * 않고 지운다.
 *
 * <ul>
 *   <li>Redis 장애면 그날은 건너뛴다 — 아직 DB에 반영되지 않은 자동 저장이 있을 수 있다.
 *   <li>트랜잭션마다 후보 100개를 {@code FOR UPDATE SKIP LOCKED}로 잠그고, Redis 보관분({@code autosave:post:{id}})이
 *       없는 글만 지운다(딸린 행은 CASCADE). 남긴 글은 다음 회차에 다시 본다. 묶음은 번호 순으로 넘어가 남긴 글을 다시 잠그지 않는다.
 *   <li>이벤트는 내지 않는다(빈 글은 누구에게도 보인 적이 없다).
 * </ul>
 */
@Component
public class EmptyDraftCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(EmptyDraftCleanupJob.class);

    static final int BATCH = 100;

    private final RedisGuard redisGuard;
    private final RedisAutosaveStore autosaves;
    private final PostMaintenanceRepository maintenance;
    private final PostAuthoringProperties properties;
    private final TransactionTemplate tx;

    public EmptyDraftCleanupJob(
            RedisGuard redisGuard,
            RedisAutosaveStore autosaves,
            PostMaintenanceRepository maintenance,
            PostAuthoringProperties properties,
            TransactionTemplate tx) {
        this.redisGuard = redisGuard;
        this.autosaves = autosaves;
        this.maintenance = maintenance;
        this.properties = properties;
        this.tx = tx;
    }

    @Scheduled(cron = "${blog.cleanup.empty-draft-cron}", zone = "${blog.cleanup.zone}")
    @SchedulerLock(name = "empty-draft-cleanup")
    public void run() {
        cleanup();
    }

    /**
     * @return 지운 글 수
     */
    public int cleanup() {
        if (!redisGuard.isAvailable()) {
            log.warn("Redis 장애로 빈 임시글 정리를 오늘은 건너뜁니다");
            return 0;
        }
        int deleted = 0;
        long afterId = 0;
        for (; ; ) {
            long from = afterId;
            Batch batch = tx.execute(status -> deleteBatch(from));
            if (batch == null) {
                break;
            }
            deleted += batch.deleted();
            afterId = batch.lastId();
            if (batch.locked() < BATCH) {
                break;
            }
            if (!redisGuard.isAvailable()) {
                log.warn("빈 임시글 정리 중 Redis 장애, 남은 후보는 다음 회차에 봅니다");
                break;
            }
        }
        if (deleted > 0) {
            log.info("빈 임시글 정리: {}건 삭제", deleted);
        }
        return deleted;
    }

    private Batch deleteBatch(long afterId) {
        List<Long> candidates =
                maintenance.lockEmptyDraftCandidates(
                        properties.cleanup().emptyDraftAge(), afterId, BATCH);
        int deleted = 0;
        long lastId = afterId;
        for (long postId : candidates) {
            lastId = postId;
            if (autosaves.mayHaveEntry(postId)) {
                continue;
            }
            deleted += maintenance.deleteById(postId);
        }
        return new Batch(candidates.size(), deleted, lastId);
    }

    private record Batch(int locked, int deleted, long lastId) {}
}
