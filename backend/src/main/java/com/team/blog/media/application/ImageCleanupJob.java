package com.team.blog.media.application;

import com.team.blog.media.infra.ImageRepository;
import com.team.blog.media.infra.ImageRepository.CleanupCandidate;
import com.team.blog.media.infra.storage.ImageStorage;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 버려진 사진 정리 (003 T081 US7, research R13, FR-041·042, 04 §4-4). 매일 {@code
 * blog.image.cleanup.cron}(기본 03:30, {@code blog.time-zone})에 연결되지 않은 지 {@code temp-ttl}(24시간)이 지난
 * TEMP 사진과 연결 해제 뒤 {@code detached-ttl}(7일)이 지난 사진의 저장소 파일과 행을 지운다. 현재 프로필 사진({@code ATTACHED},
 * {@code detached_at IS NULL})은 조건에 걸리지 않는다.
 *
 * <ol>
 *   <li>후보 SELECT ({@code batch-size}개, 번호 순) — 트랜잭션 없음
 *   <li>트랜잭션 밖에서 {@link ImageStorage#deleteAll} (없는 키는 성공)
 *   <li>원본·썸네일 삭제가 모두 성공한 사진만 조건을 다시 확인하며 행 삭제 ({@code post_image}는 CASCADE). 그사이 다시 연결된 사진은
 *       남는다(파일은 이미 지워졌으므로 경고 로그)
 * </ol>
 *
 * 저장소 삭제에 실패한 사진은 행을 남겨 다음 날 다시 시도한다. 한 묶음이 실패해도 다음 묶음을 계속하고, {@code max-duration}을 넘으면 다음 묶음을 시작하지
 * 않는다. 로그에는 개수만 쓴다(키·회원 번호 없음, FR-009).
 */
@Component
public class ImageCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(ImageCleanupJob.class);

    /**
     * 한 번의 결과.
     *
     * @param deleted 지운 사진 수
     * @param failed 저장소 삭제에 실패해 남긴 사진 수
     * @param kept 파일을 지운 뒤 다시 연결돼 행을 남긴 사진 수
     * @param batches 처리한 묶음 수
     * @param timedOut 최대 실행 시간을 넘겨 멈췄는가
     */
    public record Result(int deleted, int failed, int kept, int batches, boolean timedOut) {}

    private final ImageRepository images;
    private final ImageStorage storage;
    private final ImageProperties properties;
    private final Clock clock;

    public ImageCleanupJob(
            ImageRepository images, ImageStorage storage, ImageProperties properties, Clock clock) {
        this.images = images;
        this.storage = storage;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(cron = "${blog.image.cleanup.cron}", zone = "${blog.time-zone}")
    @SchedulerLock(name = "imageCleanup", lockAtMostFor = "PT30M")
    public void run() {
        cleanup(clock.instant(), properties.cleanup().maxDuration());
    }

    /**
     * 잠금 없이 부르는 것은 테스트·운영 도구뿐이다.
     *
     * @param now 기준 시각
     * @param maxDuration 이 시간이 지나면 다음 묶음을 시작하지 않는다 (진행 중인 묶음은 끝낸다)
     */
    public Result cleanup(Instant now, Duration maxDuration) {
        long started = System.nanoTime();
        ImageProperties.Cleanup config = properties.cleanup();
        int batchSize = config.batchSize();
        int deleted = 0;
        int failed = 0;
        int kept = 0;
        int batches = 0;
        boolean timedOut = false;
        long afterId = 0;
        for (; ; ) {
            List<CleanupCandidate> candidates =
                    images.cleanupCandidates(
                            now, config.tempTtl(), config.detachedTtl(), afterId, batchSize);
            if (candidates.isEmpty()) {
                break;
            }
            batches++;
            afterId = candidates.get(candidates.size() - 1).id();
            try {
                Batch batch = deleteBatch(candidates, now, config);
                deleted += batch.deleted();
                failed += batch.failed();
                kept += batch.kept();
            } catch (RuntimeException e) {
                // 저장소·DB 오류: 이 묶음은 남기고 다음 묶음을 계속한다 (내일 다시)
                failed += candidates.size();
                log.warn("사진 정리 묶음 실패: {}개를 다음에 다시 시도합니다", candidates.size(), e);
            }
            if (candidates.size() < batchSize) {
                break;
            }
            if (Duration.ofNanos(System.nanoTime() - started).compareTo(maxDuration) >= 0) {
                timedOut = true;
                break;
            }
        }
        if (kept > 0) {
            log.warn("사진 정리 중 다시 연결된 사진 {}장은 파일이 이미 지워졌습니다", kept);
        }
        log.info(
                "사진 정리: 삭제 {}, 실패 {}, 유지 {}, 묶음 {}{}",
                deleted,
                failed,
                kept,
                batches,
                timedOut ? " (시간 초과로 멈춤)" : "");
        return new Result(deleted, failed, kept, batches, timedOut);
    }

    private Batch deleteBatch(
            List<CleanupCandidate> candidates, Instant now, ImageProperties.Cleanup config) {
        List<String> keys = new ArrayList<>(candidates.size() * 2);
        for (CleanupCandidate c : candidates) {
            keys.add(c.storageKey());
            if (c.thumbStorageKey() != null) {
                keys.add(c.thumbStorageKey());
            }
        }
        Set<String> failedKeys = storage.deleteAll(keys);
        List<Long> removable = new ArrayList<>(candidates.size());
        int failed = 0;
        for (CleanupCandidate c : candidates) {
            boolean ok =
                    !failedKeys.contains(c.storageKey())
                            && (c.thumbStorageKey() == null
                                    || !failedKeys.contains(c.thumbStorageKey()));
            if (ok) {
                removable.add(c.id());
            } else {
                failed++;
            }
        }
        Set<Long> removed =
                new HashSet<>(
                        images.deleteIfStillEligible(
                                removable, now, config.tempTtl(), config.detachedTtl()));
        return new Batch(removed.size(), failed, removable.size() - removed.size());
    }

    private record Batch(int deleted, int failed, int kept) {}
}
