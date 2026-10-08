package com.team.blog.moderation.application;

import com.team.blog.moderation.infra.ReportCaseRepository;
import com.team.blog.moderation.infra.ReportRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.function.IntSupplier;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 매일 신고 기록 보관 정리 (014 T058, FR-032·FR-033, contracts/moderation-sql.md §8). ShedLock {@code
 * reportSnapshotCleanup}.
 *
 * <ol>
 *   <li>대상이 사라진(FK가 NULL이 된) 대기 사건을 "대상 없음"으로 닫는다 — 이벤트를 놓친 경우의 안전망
 *   <li>처리된 지 {@code retention}(30일)이 지난 사건의 신고 설명을 {@code cleanup-batch-size}개씩 비운다
 *   <li>같은 사건의 스냅샷 제목·원문을 같은 방식으로 비운다
 * </ol>
 *
 * 묶음마다 자기 트랜잭션이다. 로그에는 건수만 남긴다(내용·회원 번호 없음).
 */
@Component
public class ReportSnapshotCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(ReportSnapshotCleanupJob.class);

    private final ReportCaseRepository cases;
    private final ReportRepository reports;
    private final ModerationProperties properties;
    private final TransactionTemplate tx;
    private final Clock clock;

    public ReportSnapshotCleanupJob(
            ReportCaseRepository cases,
            ReportRepository reports,
            ModerationProperties properties,
            TransactionTemplate tx,
            Clock clock) {
        this.cases = cases;
        this.reports = reports;
        this.properties = properties;
        this.tx = tx;
        this.clock = clock;
    }

    /**
     * @param orphansClosed 대상 없음으로 닫은 사건 수
     * @param detailsCleared 비운 신고 설명 수
     * @param snapshotsCleared 비운 사건 스냅샷 수
     */
    public record Result(int orphansClosed, int detailsCleared, int snapshotsCleared) {}

    @Scheduled(cron = "${blog.moderation.cleanup-cron}", zone = "${blog.time-zone}")
    @SchedulerLock(name = "reportSnapshotCleanup", lockAtMostFor = "PT10M")
    public void run() {
        cleanup();
    }

    public Result cleanup() {
        Instant now = clock.instant();
        Instant cutoff = now.minus(properties.retention());
        int batch = properties.cleanupBatchSize();
        Integer orphanResult = tx.execute(s -> cases.closeOrphans(now));
        int orphans = orphanResult == null ? 0 : orphanResult;
        int details = repeat(() -> reports.clearDetails(cutoff, batch), batch);
        int snapshots = repeat(() -> cases.clearSnapshots(cutoff, batch), batch);
        log.info(
                "신고 기록 정리: orphansClosed={} detailsCleared={} snapshotsCleared={}",
                orphans,
                details,
                snapshots);
        return new Result(orphans, details, snapshots);
    }

    private int repeat(IntSupplier step, int batch) {
        int total = 0;
        while (true) {
            Integer n = tx.execute(s -> step.getAsInt());
            int count = n == null ? 0 : n;
            total += count;
            if (count < batch) {
                return total;
            }
        }
    }
}
