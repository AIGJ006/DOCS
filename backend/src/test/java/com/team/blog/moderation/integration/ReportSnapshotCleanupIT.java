package com.team.blog.moderation.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.moderation.application.ReportSnapshotCleanupJob;
import com.team.blog.moderation.support.ReportFixtures;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.fixture.PostFixtures;
import java.lang.reflect.Method;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;

/** 신고 기록 30일 보관 정리 (014 T056, FR-033, contracts/moderation-sql.md §8). */
class ReportSnapshotCleanupIT extends IntegrationTestBase {

    @Autowired ReportSnapshotCleanupJob job;

    @Test
    void 처리된_지_31일_지난_사건만_스냅샷과_설명을_비운다() {
        ReportFixtures f = new ReportFixtures(jdbc);
        long author = members().member().create();
        long admin = members().member().role("ADMIN").create();
        PostFixtures posts = new PostFixtures(jdbc);
        long old =
                f.handledPost(
                        posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC),
                        author,
                        "REJECTED",
                        admin,
                        Instant.now().minus(Duration.ofDays(31)));
        long recent =
                f.handledPost(
                        posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC),
                        author,
                        "HIDDEN",
                        admin,
                        Instant.now().minus(Duration.ofDays(29)));
        long pending =
                f.pendingPost(posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC), author);
        jdbc.update(
                "UPDATE report_case SET created_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minus(Duration.ofDays(60))),
                pending);
        long oldReport = f.report(old, members().member().create(), "OTHER", "오래된 설명");
        long recentReport = f.report(recent, members().member().create(), "OTHER", "최근 설명");
        long pendingReport = f.report(pending, members().member().create(), "OTHER", "대기 설명");

        ReportSnapshotCleanupJob.Result result = job.cleanup();

        assertThat(result.detailsCleared()).isEqualTo(1);
        assertThat(result.snapshotsCleared()).isEqualTo(1);
        assertThat(snapshot(old)).isNull();
        assertThat(snapshot(recent)).isNotNull();
        assertThat(snapshot(pending)).isNotNull();
        assertThat(detail(oldReport)).isNull();
        assertThat(detail(recentReport)).isEqualTo("최근 설명");
        assertThat(detail(pendingReport)).isEqualTo("대기 설명");
        assertThat(f.status(old)).isEqualTo("REJECTED");
    }

    @Test
    void 천_건이_넘어도_묶음을_반복해_모두_비운다() {
        long author = members().member().create();
        long admin = members().member().role("ADMIN").create();
        long postId = new PostFixtures(jdbc).create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        jdbc.update(
                "INSERT INTO report_case (target_type, post_id, target_author_id, snapshot_title,"
                        + " snapshot_content, status, handled_by, handled_at, created_at)"
                        + " SELECT 'POST', ?, ?, '제목', '내용', 'REJECTED', ?, ?, ? FROM"
                        + " generate_series(1, 1205)",
                postId,
                author,
                admin,
                Timestamp.from(Instant.now().minus(Duration.ofDays(40))),
                Timestamp.from(Instant.now().minus(Duration.ofDays(41))));

        assertThat(job.cleanup().snapshotsCleared()).isEqualTo(1205);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM report_case WHERE snapshot_content IS NOT NULL",
                                Integer.class))
                .isZero();
    }

    @Test
    void 매일_04시45분_ShedLock_reportSnapshotCleanup() throws NoSuchMethodException {
        Method run = ReportSnapshotCleanupJob.class.getMethod("run");
        assertThat(run.getAnnotation(Scheduled.class).cron())
                .isEqualTo("${blog.moderation.cleanup-cron}");
        assertThat(run.getAnnotation(Scheduled.class).zone()).isEqualTo("${blog.time-zone}");
        assertThat(run.getAnnotation(SchedulerLock.class).name())
                .isEqualTo("reportSnapshotCleanup");
    }

    private String snapshot(long caseId) {
        return jdbc.queryForObject(
                "SELECT snapshot_content FROM report_case WHERE id = ?", String.class, caseId);
    }

    private String detail(long reportId) {
        return jdbc.queryForObject(
                "SELECT detail FROM report WHERE id = ?", String.class, reportId);
    }
}
