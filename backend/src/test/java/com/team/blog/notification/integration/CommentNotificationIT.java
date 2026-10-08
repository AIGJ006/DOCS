package com.team.blog.notification.integration;

import static com.team.blog.interaction.support.CommentApi.id;
import static com.team.blog.interaction.support.CommentApi.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.interaction.support.CommentApi;
import com.team.blog.notification.support.ExecutorBlocker;
import com.team.blog.notification.support.NotificationTestBase;
import com.team.blog.support.TestLogin;
import com.team.blog.support.fixture.PostFixtures.State;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 댓글·답글 알림 (011 T015·T039, US1 #1~#3, US4 #5, research R6). 007 댓글 API로 만들고 {@code
 * CommentCreated}·{@code CommentDeleted} 리스너가 처리한 결과를 본다.
 */
class CommentNotificationIT extends NotificationTestBase {

    @org.springframework.beans.factory.annotation.Autowired
    private com.team.blog.post.application.PostPurgeService postPurge;

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.transaction.support.TransactionTemplate tx;

    private CommentApi comments;
    private long a;
    private long b;
    private long c;
    private long d;
    private long postId;

    @BeforeEach
    void setUp() {
        comments = new CommentApi(mockMvc);
        a = members().member().create();
        b = members().member().create();
        c = members().member().create();
        d = members().member().create();
        postId = posts.create(a, State.PUBLISHED_PUBLIC);
    }

    @Test
    void 최상위_댓글은_글_작성자에게_COMMENT() throws Exception {
        long commentId = comment(b, "첫 댓글", null);

        awaiter.untilCount(a, 1);
        Map<String, Object> row = notifications.rows(a).get(0);
        assertThat(row.get("type")).isEqualTo("COMMENT");
        assertThat(((Number) row.get("post_id")).longValue()).isEqualTo(postId);
        assertThat(((Number) row.get("comment_id")).longValue()).isEqualTo(commentId);
        assertThat(((Number) row.get("last_actor_id")).longValue()).isEqualTo(b);
        assertThat(((Number) row.get("actor_count")).intValue()).isEqualTo(1);
        assertThat(row.get("group_key")).isNull();
        assertThat(row.get("read_at")).isNull();
        assertThat(notifications.count(b)).isZero();
    }

    @Test
    void 답글은_대상에게_REPLY_글_작성자에게_COMMENT() throws Exception {
        long root = comment(b, "최상위", null);
        awaiter.untilCount(a, 1);

        long reply = comment(c, "답글", root);

        awaiter.untilCount(b, 1);
        awaiter.untilCount(a, 2);
        assertThat(notifications.rows(b).get(0).get("type")).isEqualTo("REPLY");
        assertThat(((Number) notifications.rows(b).get(0).get("comment_id")).longValue())
                .isEqualTo(reply);
        assertThat(notifications.count(a, "COMMENT")).isEqualTo(2);
        assertThat(notifications.count(c)).isZero();
    }

    @Test
    void 답글의_답글은_그_답글_작성자에게만_REPLY() throws Exception {
        long root = comment(b, "최상위", null);
        long replyOfC = comment(c, "C 답글", root);
        awaiter.untilCount(b, 1);
        awaiter.untilCount(a, 2);

        long replyOfD = comment(d, "D가 C에게", replyOfC);

        awaiter.untilCount(c, 1);
        awaiter.untilCount(a, 3);
        assertThat(notifications.rows(c).get(0).get("type")).isEqualTo("REPLY");
        assertThat(((Number) notifications.rows(c).get(0).get("comment_id")).longValue())
                .isEqualTo(replyOfD);
        // 최상위 작성자 B는 더 받지 않는다 (C 답글의 REPLY 하나만)
        assertThat(notifications.count(b)).isEqualTo(1);
    }

    @Test
    void 답글_대상이_글_작성자면_REPLY_하나만() throws Exception {
        long root = comment(a, "작성자 최상위", null);
        awaiter.idle();
        assertThat(notifications.count(a)).isZero();

        comment(b, "작성자에게 답글", root);

        awaiter.untilCount(a, 1);
        assertThat(notifications.rows(a).get(0).get("type")).isEqualTo("REPLY");
    }

    @Test
    void 처리_전에_댓글이_지워졌으면_만들지_않는다() throws Exception {
        long commentId;
        try (ExecutorBlocker ignored = ExecutorBlocker.block(eventExecutor)) {
            commentId = comment(b, "곧 지움", null);
            assertThat(status(comments.delete(TestLogin.loginAs(mockMvc, b), commentId)))
                    .isEqualTo(204);
        }
        awaiter.idle();
        assertThat(notifications.count(a)).isZero();
    }

    @Test
    void 댓글을_지우면_그_댓글의_알림만_지운다() throws Exception {
        long root = comment(b, "최상위", null);
        long reply = comment(c, "답글", root);
        long other = comment(d, "다른 댓글", null);
        awaiter.untilCount(b, 1);
        awaiter.untilCount(a, 3);

        // 답글이 있어 자리로 남는 최상위 삭제
        assertThat(status(comments.delete(TestLogin.loginAs(mockMvc, b), root))).isEqualTo(204);
        awaiter.idle();
        assertThat(commentNotifications(root)).isZero();
        assertThat(commentNotifications(reply)).isEqualTo(2);

        assertThat(status(comments.delete(TestLogin.loginAs(mockMvc, c), reply))).isEqualTo(204);
        awaiter.idle();
        assertThat(commentNotifications(reply)).isZero();
        assertThat(commentNotifications(other)).isEqualTo(1);
    }

    @Test
    void 글을_완전히_지우면_그_글의_알림도_사라진다() throws Exception {
        comment(b, "최상위", null);
        awaiter.untilCount(a, 1);
        notifications.single(c, "NEW_POST").post(postId).actor(a).create();

        tx.executeWithoutResult(status -> postPurge.purge(postId, a, true));

        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM notification WHERE post_id = ?",
                                Long.class,
                                postId))
                .isZero();
    }

    private long commentNotifications(long commentId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM notification WHERE comment_id = ?", Long.class, commentId);
    }

    private long comment(long author, String content, Long replyTo) throws Exception {
        Cookie session = TestLogin.loginAs(mockMvc, author);
        var result = comments.create(session, postId, content, replyTo);
        assertThat(status(result)).as(CommentApi.body(result)).isEqualTo(201);
        return id(result);
    }
}
