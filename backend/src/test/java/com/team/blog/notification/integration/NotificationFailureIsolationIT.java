package com.team.blog.notification.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.notification.support.NotificationActions;
import com.team.blog.notification.support.NotificationTestBase;
import com.team.blog.notification.support.NotificationWriterFailureSwitch;
import com.team.blog.support.fixture.PostFixtures.State;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * 알림 처리 실패 격리 (011 T018, US1 #8, SC-009, FR-002). {@code NotificationWriter}가 예외를 던져도 댓글·좋아요·발행
 * API는 성공하고, WARN 로그에는 이벤트 종류와 번호만 남는다(제목·닉네임 없음).
 */
@ExtendWith(OutputCaptureExtension.class)
class NotificationFailureIsolationIT extends NotificationTestBase {

    @AfterEach
    void restore() {
        NotificationWriterFailureSwitch.fail(false);
    }

    @Test
    void 알림_저장이_실패해도_원래_행동은_성공한다(CapturedOutput output) throws Exception {
        NotificationActions actions = new NotificationActions(mockMvc, jdbc);
        long a = members().member().nickname("글쓴이닉네임").create();
        long b = members().member().nickname("댓글러닉네임").create();
        long follower = members().member().create();
        actions.followRow(follower, a);
        long postId = posts.post(a).title("비밀스러운제목").published("PUBLIC").create();

        NotificationWriterFailureSwitch.fail(true);

        long commentId = actions.comment(b, postId, "실패해도 남는 댓글", null);
        actions.like(b, postId);
        actions.follow(b, a);
        long newPost = actions.publish(a, "또다른비밀제목", "PUBLIC");
        awaiter.idle();

        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM comment WHERE id = ?", Long.class, commentId))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM post_like WHERE post_id = ? AND member_id = ?",
                                Long.class,
                                postId,
                                b))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM post WHERE id = ?", String.class, newPost))
                .isEqualTo("PUBLISHED");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM follow WHERE follower_id = ? AND followee_id = ?",
                                Long.class,
                                b,
                                a))
                .isEqualTo(1);
        assertThat(notifications.count(a)).isZero();
        assertThat(notifications.count(follower)).isZero();

        String out = output.getOut();
        assertThat(out)
                .contains("알림 처리 실패: event=CommentCreated, targetId=" + commentId)
                .contains("알림 처리 실패: event=PostLiked, targetId=" + postId)
                .contains("알림 처리 실패: event=MemberFollowed, targetId=" + a)
                .contains("알림 처리 실패: event=PostWentPublic, targetId=" + newPost);
        assertThat(out)
                .doesNotContain("비밀스러운제목")
                .doesNotContain("또다른비밀제목")
                .doesNotContain("댓글러닉네임")
                .doesNotContain("실패해도 남는 댓글");
    }

    @Test
    void 스위치를_끄면_다시_만든다() throws Exception {
        NotificationActions actions = new NotificationActions(mockMvc, jdbc);
        long a = members().member().create();
        long b = members().member().create();
        long postId = posts.create(a, State.PUBLISHED_PUBLIC);
        actions.comment(b, postId, "정상", null);
        awaiter.untilCount(a, 1);
    }
}
