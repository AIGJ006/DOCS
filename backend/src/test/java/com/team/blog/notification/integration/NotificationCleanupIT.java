package com.team.blog.notification.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.notification.application.NotificationCleanupJob;
import com.team.blog.notification.support.NotificationTestBase;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.scheduling.annotation.Scheduled;

/** 매일 정리 (011 T052, US7 #1·#2, SC-008, contracts §10). 작업 메서드를 직접 부른다. */
@ExtendWith(OutputCaptureExtension.class)
class NotificationCleanupIT extends NotificationTestBase {

    @Autowired private NotificationCleanupJob job;

    private long a;
    private long b;

    @BeforeEach
    void setUp() {
        a = members().member().create();
        b = members().member().create();
    }

    /** 행을 많이 넣을 때는 한 문장으로 (updated_at = base - i초). */
    private void bulk(long receiver, int count, Instant base) {
        jdbc.update(
                "INSERT INTO notification (receiver_id, type, result, actor_count, created_at, updated_at)"
                        + " SELECT ?, 'REPORT_RESOLVED', 'ACTION_TAKEN', 0, ts, ts FROM ("
                        + "   SELECT ?::timestamptz - (g || ' seconds')::interval AS ts"
                        + "     FROM generate_series(0, ? - 1) g) t",
                receiver,
                java.sql.Timestamp.from(base),
                count);
    }

    @Test
    void 구십일_지난_알림은_지우고_89일은_남긴다(CapturedOutput output) {
        Instant now = Instant.now();
        long old =
                notifications
                        .single(a, "REPORT_RESOLVED")
                        .at(now.minus(91, ChronoUnit.DAYS))
                        .create();
        long young =
                notifications
                        .single(a, "REPORT_RESOLVED")
                        .at(now.minus(89, ChronoUnit.DAYS))
                        .create();

        NotificationCleanupJob.Result result = job.cleanup();

        List<Long> left = jdbc.queryForList("SELECT id FROM notification ORDER BY id", Long.class);
        assertThat(left).containsExactly(young).doesNotContain(old);
        assertThat(result.expired()).isEqualTo(1);
        assertThat(output.getOut()).contains("알림 정리").contains("expired=1");
    }

    @Test
    void 구십일_지난_2500개는_1000개씩_묶어_지운다() {
        bulk(a, 2500, Instant.now().minus(100, ChronoUnit.DAYS));
        NotificationCleanupJob.Result result = job.cleanup();
        assertThat(result.expired()).isEqualTo(2500);
        assertThat(result.expiredBatches()).isEqualTo(3);
        assertThat(notifications.count(a)).isZero();
    }

    @Test
    void 오늘_받은_회원은_최신_1000개만_남는다_경계는_같은_시각_포함() {
        Instant base = Instant.now().minus(1, ChronoUnit.HOURS);
        bulk(a, 999, base);
        // 1,000번째와 1,001번째가 같은 updated_at — id가 큰 쪽이 남는다
        Instant edge = base.minusSeconds(5000);
        long older = notifications.single(a, "REPORT_RESOLVED").at(edge).create();
        long newer = notifications.single(a, "REPORT_RESOLVED").at(edge).create();

        NotificationCleanupJob.Result result = job.cleanup();

        assertThat(result.trimmed()).isEqualTo(1);
        assertThat(notifications.count(a)).isEqualTo(1000);
        List<Long> ids =
                jdbc.queryForList(
                        "SELECT id FROM notification WHERE id IN (?, ?)", Long.class, older, newer);
        assertThat(ids).containsExactly(newer);
    }

    @Test
    void 오늘_받지_않은_회원은_1200개여도_대상이_아니다() {
        bulk(b, 1200, Instant.now().minus(3, ChronoUnit.DAYS));
        bulk(a, 10, Instant.now().minus(1, ChronoUnit.HOURS));

        NotificationCleanupJob.Result result = job.cleanup();

        assertThat(result.trimmed()).isZero();
        assertThat(notifications.count(b)).isEqualTo(1200);
    }

    @Test
    void 매일_04시30분_ShedLock_notificationCleanup() throws Exception {
        Method run = NotificationCleanupJob.class.getMethod("run");
        assertThat(run.getAnnotation(Scheduled.class).cron())
                .isEqualTo("${blog.notification.cleanup.cron}");
        assertThat(run.getAnnotation(Scheduled.class).zone()).isEqualTo("${blog.time-zone}");
        assertThat(run.getAnnotation(SchedulerLock.class).name()).isEqualTo("notificationCleanup");
    }
}
