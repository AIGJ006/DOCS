package com.team.blog.notification.application;

import com.team.blog.notification.infra.NotificationRepository;
import java.time.Clock;
import java.time.Instant;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 매일 알림 정리 (011 T054, research R13, contracts §10, FR-037). ShedLock {@code notificationCleanup}.
 *
 * <ol>
 *   <li>{@code updated_at}이 보관 기간({@code retention}, 90일)보다 오래된 알림을 {@code batch-size}개씩 0행이 될 때까지
 *       지운다 (묶음마다 트랜잭션).
 *   <li>최근 {@code recent-window}(하루) 안에 알림을 받은 사람만 골라 최신 {@code max-per-member}(1,000)개를 넘는 것을 지운다
 *       — 비용이 그날 받은 사람 수에 비례한다.
 * </ol>
 *
 * 지운 수를 INFO로 남긴다.
 */
@Component
public class NotificationCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(NotificationCleanupJob.class);

    private final NotificationRepository notifications;
    private final NotificationProperties properties;
    private final TransactionTemplate tx;
    private final Clock clock;

    public NotificationCleanupJob(
            NotificationRepository notifications,
            NotificationProperties properties,
            TransactionTemplate tx,
            Clock clock) {
        this.notifications = notifications;
        this.properties = properties;
        this.tx = tx;
        this.clock = clock;
    }

    /**
     * @param expired 보관 기간이 지나 지운 수
     * @param expiredBatches 그 삭제 묶음 수 (0행 확인 묶음 제외)
     * @param trimmed 사람당 개수를 넘어 지운 수
     */
    public record Result(int expired, int expiredBatches, int trimmed) {}

    @Scheduled(cron = "${blog.notification.cleanup.cron}", zone = "${blog.time-zone}")
    @SchedulerLock(name = "notificationCleanup", lockAtMostFor = "PT1H")
    public void run() {
        cleanup();
    }

    public Result cleanup() {
        Instant now = clock.instant();
        Instant cutoff = now.minus(properties.retention());
        int batch = properties.cleanup().batchSize();
        int expired = 0;
        int batches = 0;
        while (true) {
            Integer deleted = tx.execute(s -> notifications.deleteOlderThan(cutoff, batch));
            int n = deleted == null ? 0 : deleted;
            if (n == 0) {
                break;
            }
            expired += n;
            batches++;
            if (n < batch) {
                break;
            }
        }
        Instant since = now.minus(properties.cleanup().recentWindow());
        Integer trimmedResult =
                tx.execute(s -> notifications.trimPerMember(since, properties.maxPerMember()));
        int trimmed = trimmedResult == null ? 0 : trimmedResult;
        log.info("알림 정리: expired={} batches={} trimmed={}", expired, batches, trimmed);
        return new Result(expired, batches, trimmed);
    }
}
