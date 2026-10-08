package com.team.blog.moderation.integration;

import static com.team.blog.moderation.support.ReportApi.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.team.blog.interaction.support.CommentApi;
import com.team.blog.interaction.support.CommentFixtures;
import com.team.blog.moderation.application.ReportSnapshotCleanupJob;
import com.team.blog.moderation.application.ReportWithdrawalPurgeStep;
import com.team.blog.moderation.support.ReportApi;
import com.team.blog.moderation.support.ReportFixtures;
import com.team.blog.post.application.PostPurgeService;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import com.team.blog.support.fixture.PostFixtures;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/** 대상이 사라지거나 바뀐 사건 (014 T055·T060·T062, FR-031·FR-034). */
class OrphanCaseIT extends IntegrationTestBase {

    @Autowired PostPurgeService purgeService;
    @Autowired ReportWithdrawalPurgeStep withdrawalStep;
    @Autowired ReportSnapshotCleanupJob cleanupJob;
    @Autowired TransactionTemplate tx;

    private ReportFixtures reports;
    private PostFixtures posts;
    private CommentFixtures comments;
    private long author;
    private long reporter;

    @BeforeEach
    void setUp() {
        reports = new ReportFixtures(jdbc);
        posts = new PostFixtures(jdbc);
        comments = new CommentFixtures(jdbc);
        author = members().member().create();
        reporter = members().member().create();
    }

    private long pendingPost(long postId) {
        long caseId = reports.pendingPost(postId, author);
        reports.report(caseId, reporter, "SPAM", null);
        return caseId;
    }

    @Test
    void 글을_완전히_지우면_그_글과_댓글의_대기_사건이_닫히고_스냅샷은_남는다() {
        long postId = posts.create(author, PostFixtures.State.TRASHED);
        long postCase = pendingPost(postId);
        long commenter = members().member().create();
        long commentId = comments.on(postId, commenter).content("글과 함께 사라질 댓글").create();
        long commentCase = reports.pendingComment(commentId, commenter);
        reports.report(commentCase, reporter, "ABUSE", null);

        tx.executeWithoutResult(s -> purgeService.purge(postId, author, false));

        assertThat(reports.status(postCase)).isEqualTo("CLOSED_NO_TARGET");
        assertThat(reports.status(commentCase)).isEqualTo("CLOSED_NO_TARGET");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT snapshot_content FROM report_case WHERE id = ?",
                                String.class,
                                commentCase))
                .isEqualTo("글과 함께 사라질 댓글");
        assertThat(reports.reportCount()).isEqualTo(2);
    }

    @Test
    void 댓글을_본인이_지우면_자리로_남아도_사건이_닫힌다() throws Exception {
        long postId = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        long commenter = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, commenter);
        long gone = comments.on(postId, commenter).create();
        long withReply = comments.on(postId, commenter).create();
        comments.on(postId, members().member().create()).parent(withReply).create();
        long goneCase = reports.pendingComment(gone, commenter);
        long placeholderCase = reports.pendingComment(withReply, commenter);

        CommentApi api = new CommentApi(mockMvc);
        assertThat(CommentApi.status(api.delete(session, gone))).isBetween(200, 204);
        assertThat(CommentApi.status(api.delete(session, withReply))).isBetween(200, 204);

        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(
                        () -> {
                            assertThat(reports.status(goneCase)).isEqualTo("CLOSED_NO_TARGET");
                            assertThat(reports.status(placeholderCase))
                                    .isEqualTo("CLOSED_NO_TARGET");
                        });
    }

    @Test
    void 휴지통_이동과_비공개_전환만으로는_대기_사건이_그대로() {
        long trashed = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        long privated = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        long c1 = pendingPost(trashed);
        long c2 = pendingPost(privated);
        jdbc.update("UPDATE post SET deleted_at = now() WHERE id = ?", trashed);
        jdbc.update("UPDATE post SET visibility = 'PRIVATE' WHERE id = ?", privated);

        cleanupJob.cleanup();

        assertThat(reports.status(c1)).isEqualTo("PENDING");
        assertThat(reports.status(c2)).isEqualTo("PENDING");
    }

    @Test
    void 매일_정리가_이벤트를_놓친_고아_사건을_닫는다() {
        long postId = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        long caseId = pendingPost(postId);
        jdbc.update("DELETE FROM post WHERE id = ?", postId);

        assertThat(cleanupJob.cleanup().orphansClosed()).isEqualTo(1);
        assertThat(reports.status(caseId)).isEqualTo("CLOSED_NO_TARGET");
    }

    @Test
    void 탈퇴_정리_order_80은_작성자의_대기_사건을_닫고_신고자의_설명을_비운다() {
        long postId = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        long authorCase = pendingPost(postId);
        long otherAuthor = members().member().create();
        long otherPost = posts.create(otherAuthor, PostFixtures.State.PUBLISHED_PUBLIC);
        long otherCase = reports.pendingPost(otherPost, otherAuthor);
        long reportId = reports.report(otherCase, author, "OTHER", "탈퇴자가 쓴 설명");

        assertThat(withdrawalStep.order()).isEqualTo(80);
        tx.executeWithoutResult(s -> withdrawalStep.purge(author));

        assertThat(reports.status(authorCase)).isEqualTo("CLOSED_NO_TARGET");
        assertThat(reports.status(otherCase)).isEqualTo("PENDING");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT detail FROM report WHERE id = ?", String.class, reportId))
                .isNull();
    }

    @Test
    void 작성자가_탈퇴_신청하면_사건은_대기_그대로_현재_상태는_작성자_탈퇴() throws Exception {
        long postId = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        long caseId = pendingPost(postId);
        posts.withdraw(author);
        Cookie admin = TestLogin.loginAs(mockMvc, members().member().role("ADMIN").create());

        assertThat(reports.status(caseId)).isEqualTo("PENDING");
        String state = read(new ReportApi(mockMvc).detail(admin, caseId), "$.currentState");
        assertThat(state).isEqualTo("AUTHOR_WITHDRAWN");
    }

    @Test
    void 신고자가_탈퇴_신청해도_신고_기록은_그대로() {
        long postId = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        long caseId = reports.pendingPost(postId, author);
        long reportId = reports.report(caseId, reporter, "OTHER", "남길 설명");
        posts.withdraw(reporter);

        assertThat(reports.reportCount()).isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT detail FROM report WHERE id = ?", String.class, reportId))
                .isEqualTo("남길 설명");
        assertThat(reports.status(caseId)).isEqualTo("PENDING");
    }
}
