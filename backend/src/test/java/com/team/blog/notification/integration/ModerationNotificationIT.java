package com.team.blog.notification.integration;

import static com.team.blog.notification.support.NotificationApi.body;
import static com.team.blog.notification.support.NotificationApi.read;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.interaction.support.CommentFixtures;
import com.team.blog.notification.support.NotificationApi;
import com.team.blog.notification.support.NotificationTestBase;
import com.team.blog.shared.event.ContentHidden;
import com.team.blog.shared.event.ReportResolved;
import com.team.blog.shared.event.ReportResult;
import com.team.blog.shared.event.ReportTargetType;
import com.team.blog.support.TestLogin;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 운영 알림 (011 T043, US5 #1~#6, SC-010). 014가 아직 없어 {@code ReportResolved}·{@code ContentHidden}을 직접
 * 발행한다. 숨김 상태는 글·댓글 행을 직접 바꾼다.
 */
class ModerationNotificationIT extends NotificationTestBase {

    private NotificationApi api;
    private long admin;
    private long author;
    private long reporter;
    private long postId;

    @BeforeEach
    void setUp() {
        api = new NotificationApi(mockMvc);
        admin = members().member().role("ADMIN").create();
        author = members().member().handle("hiddenauthor").create();
        reporter = members().member().create();
        postId = posts.post(author).title("숨겨질 제목").published("PUBLIC").create();
    }

    private long report(String targetType, Long commentId) {
        long caseId =
                jdbc.queryForObject(
                        "INSERT INTO report_case (target_type, post_id, comment_id, target_author_id, status,"
                                + " handled_by, handled_at) VALUES (?, ?, ?, ?, 'HIDDEN', ?, now()) RETURNING id",
                        Long.class,
                        targetType,
                        postId,
                        commentId,
                        author,
                        admin);
        return jdbc.queryForObject(
                "INSERT INTO report (case_id, reporter_id, reason) VALUES (?, ?, 'SPAM') RETURNING id",
                Long.class,
                caseId,
                reporter);
    }

    private Map<String, Object> first(long member) throws Exception {
        MvcResult result = api.list(TestLogin.loginAs(mockMvc, member), 10, null);
        assertThat(result.getResponse().getStatus()).as(body(result)).isEqualTo(200);
        assertThat(body(result))
                .as("신고자·관리자 번호가 응답에 없다")
                .doesNotContain("reporterId")
                .doesNotContain("\"handledBy\"");
        return read(result, "$.items[0]");
    }

    @Test
    void 신고_결과_두_가지() throws Exception {
        long r1 = report("POST", null);
        eventPublisher.publish(
                new ReportResolved(
                        r1,
                        reporter,
                        ReportTargetType.POST,
                        postId,
                        ReportResult.ACTION_TAKEN,
                        Instant.now()));
        awaiter.untilCount(reporter, 1);

        Map<String, Object> item = first(reporter);
        assertThat(item.get("type")).isEqualTo("REPORT_RESOLVED");
        assertThat(item.get("report")).isEqualTo(Map.of("result", "ACTION_TAKEN"));
        assertThat(item.get("actor")).isNull();
        assertThat(item.get("post")).isNull();
        assertThat(item.get("url")).isNull();
        Map<String, Object> row = notifications.rows(reporter).get(0);
        assertThat(row.get("last_actor_id")).isNull();
        assertThat(((Number) row.get("actor_count")).intValue()).isZero();
        assertThat(row.get("post_id")).isNull();

        jdbc.update("DELETE FROM notification");
        eventPublisher.publish(
                new ReportResolved(
                        r1,
                        reporter,
                        ReportTargetType.POST,
                        postId,
                        ReportResult.NO_VIOLATION,
                        Instant.now()));
        awaiter.untilCount(reporter, 1);
        assertThat(first(reporter).get("report")).isEqualTo(Map.of("result", "NO_VIOLATION"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void 글_숨김은_작성자에게_제목과_사유_해제_뒤엔_사유_없음() throws Exception {
        jdbc.update(
                "UPDATE post SET hidden_at = now(), hidden_by = ?, hidden_reason = 'SPAM' WHERE id = ?",
                admin,
                postId);
        eventPublisher.publish(
                new ContentHidden(ReportTargetType.POST, postId, author, postId, Instant.now()));
        awaiter.untilCount(author, 1);

        Map<String, Object> item = first(author);
        assertThat(item.get("type")).isEqualTo("CONTENT_HIDDEN");
        assertThat(item.get("actor")).isNull();
        assertThat(((Map<?, ?>) item.get("post")).get("title")).isEqualTo("숨겨질 제목");
        assertThat(item.get("hidden"))
                .isEqualTo(Map.of("targetType", "POST", "stillHidden", true, "reason", "SPAM"));
        assertThat(item.get("url")).isEqualTo("/@hiddenauthor/posts/" + postId);

        jdbc.update(
                "UPDATE post SET hidden_at = NULL, hidden_by = NULL, hidden_reason = NULL WHERE id = ?",
                postId);
        Map<String, Object> hidden = (Map<String, Object>) first(author).get("hidden");
        assertThat(hidden).containsEntry("stillHidden", false).containsEntry("reason", null);
    }

    @Test
    void 댓글_숨김은_글_제목_없이_댓글_위치_그_댓글_알림_삭제() throws Exception {
        long commenter = members().member().create();
        long commentId = new CommentFixtures(jdbc).on(postId, commenter).create();
        notifications
                .single(author, "COMMENT")
                .post(postId)
                .comment(commentId)
                .actor(commenter)
                .create();
        jdbc.update(
                "UPDATE comment SET hidden_at = now(), hidden_by = ?, hidden_reason = 'ABUSE' WHERE id = ?",
                admin,
                commentId);

        eventPublisher.publish(
                new ContentHidden(
                        ReportTargetType.COMMENT, commentId, commenter, postId, Instant.now()));
        awaiter.untilCount(commenter, 1);

        assertThat(notifications.count(author)).isZero();
        Map<String, Object> item = first(commenter);
        assertThat(item.get("post")).isNull();
        assertThat(item.get("hidden"))
                .isEqualTo(Map.of("targetType", "COMMENT", "stillHidden", true, "reason", "ABUSE"));
        assertThat(item.get("url"))
                .isEqualTo(
                        "/@hiddenauthor/posts/"
                                + postId
                                + "?comment="
                                + commentId
                                + "#comment-"
                                + commentId);
        assertThat(body(api.list(TestLogin.loginAs(mockMvc, commenter), 10, null)))
                .doesNotContain("숨겨질 제목");
    }

    @Test
    void 모두_끈_회원도_받고_유예면_받지_않는다() {
        notifications.mute(author, "COMMENT", "REPLY", "LIKE", "FOLLOW", "NEW_POST");
        eventPublisher.publish(
                new ContentHidden(ReportTargetType.POST, postId, author, postId, Instant.now()));
        awaiter.untilCount(author, 1);

        posts.withdraw(reporter);
        long r = report("POST", null);
        eventPublisher.publish(
                new ReportResolved(
                        r,
                        reporter,
                        ReportTargetType.POST,
                        postId,
                        ReportResult.ACTION_TAKEN,
                        Instant.now()));
        awaiter.idle();
        assertThat(notifications.count(reporter)).isZero();
    }

    @Test
    void 신고_행이_지워지면_report_id만_NULL() {
        long r = report("POST", null);
        eventPublisher.publish(
                new ReportResolved(
                        r,
                        reporter,
                        ReportTargetType.POST,
                        postId,
                        ReportResult.ACTION_TAKEN,
                        Instant.now()));
        awaiter.untilCount(reporter, 1);

        jdbc.update("DELETE FROM report WHERE id = ?", r);

        Map<String, Object> row = notifications.rows(reporter).get(0);
        assertThat(row.get("report_id")).isNull();
        assertThat(row.get("result")).isEqualTo("ACTION_TAKEN");
    }
}
