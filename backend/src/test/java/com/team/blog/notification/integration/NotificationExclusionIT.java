package com.team.blog.notification.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.interaction.support.CommentFixtures;
import com.team.blog.notification.support.ExecutorBlocker;
import com.team.blog.notification.support.NotificationActions;
import com.team.blog.notification.support.NotificationTestBase;
import com.team.blog.shared.event.CommentCreated;
import com.team.blog.shared.event.PostLiked;
import com.team.blog.support.fixture.PostFixtures.State;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 공통 제외 규칙 (011 T016, SC-001·SC-004, US1 #4·#5·#7, contracts §2). "생기지 않음"은 실행기가 빈 뒤 확인한다.
 *
 * <p>US1 #5 "받는 사람이 읽을 수 없음": 댓글·좋아요 알림의 받는 사람은 글 작성자라 자기 비공개 글은 읽을 수 있다. 그래서 그 경우는 처리 전 휴지통으로
 * 옮겨(작성자도 읽을 수 없음) 확인하고, 비공개는 작성자가 아닌 받는 사람(답글 대상·새 글 팔로워)으로 확인한다.
 */
class NotificationExclusionIT extends NotificationTestBase {

    private NotificationActions actions;
    private long a;
    private long b;
    private long c;
    private long postId;

    @BeforeEach
    void setUp() {
        actions = new NotificationActions(mockMvc, jdbc);
        a = members().member().create();
        b = members().member().create();
        c = members().member().create();
        postId = posts.create(a, State.PUBLISHED_PUBLIC);
    }

    @Test
    void 내_글에_내_댓글은_0() throws Exception {
        actions.comment(a, postId, "내가 내 글에", null);
        awaiter.idle();
        assertThat(notifications.count(a)).isZero();
    }

    @Test
    void 내_글에_내_좋아요_이벤트도_0() {
        actions.likeRow(postId, a);
        eventPublisher.publish(new PostLiked(postId, a, a, Instant.now()));
        awaiter.idle();
        assertThat(notifications.count(a)).isZero();
    }

    @Test
    void 처리_전에_휴지통으로_옮긴_글의_댓글_좋아요는_0() throws Exception {
        try (ExecutorBlocker ignored = ExecutorBlocker.block(eventExecutor)) {
            actions.comment(b, postId, "곧 휴지통", null);
            actions.like(c, postId);
            jdbc.update("UPDATE post SET deleted_at = now() WHERE id = ?", postId);
        }
        awaiter.idle();
        assertThat(notifications.count(a)).isZero();
    }

    @Test
    void 처리_전에_비공개로_바뀐_글의_답글_새_글은_0() throws Exception {
        long root = actions.comment(b, postId, "최상위", null);
        awaiter.untilCount(a, 1);
        actions.followRow(c, a);
        long newPost;
        try (ExecutorBlocker ignored = ExecutorBlocker.block(eventExecutor)) {
            actions.comment(c, postId, "답글", root);
            newPost = actions.publish(a, "새 글", "PUBLIC");
            actions.changeVisibility(a, postId, "PRIVATE");
            actions.changeVisibility(a, newPost, "PRIVATE");
        }
        awaiter.idle();
        // 답글 대상 B는 이제 읽을 수 없다
        assertThat(notifications.count(b)).isZero();
        // 팔로워 C도 새 글을 읽을 수 없다
        assertThat(notifications.count(c)).isZero();
        // 작성자 A는 자기 비공개 글을 읽을 수 있어 답글의 댓글 알림은 받는다
        assertThat(notifications.count(a, "COMMENT")).isEqualTo(2);
    }

    @Test
    void 받는_사람_탈퇴_유예면_0() {
        // 007은 유예 회원의 댓글에 답글을 막으므로 답글 행을 직접 넣고 이벤트를 발행한다
        CommentFixtures comments = new CommentFixtures(jdbc);
        long root = comments.on(postId, b).content("최상위").create();
        long reply = comments.on(postId, c).parent(root).content("답글").create();
        posts.withdraw(b);

        eventPublisher.publish(
                new CommentCreated(reply, postId, a, c, root, b, null, Instant.now()));

        awaiter.untilCount(a, 1);
        assertThat(notifications.count(b)).isZero();
    }

    @Test
    void 행동자_탈퇴_유예면_0() {
        long commentId = new CommentFixtures(jdbc).on(postId, b).content("유예 전 댓글").create();
        actions.likeRow(postId, b);
        posts.withdraw(b);

        eventPublisher.publish(
                new CommentCreated(commentId, postId, a, b, null, null, null, Instant.now()),
                new PostLiked(postId, a, b, Instant.now()));

        awaiter.idle();
        assertThat(notifications.count(a)).isZero();
    }

    @Test
    void 좋아요_취소가_먼저_처리되면_0() throws Exception {
        try (ExecutorBlocker ignored = ExecutorBlocker.block(eventExecutor)) {
            actions.like(b, postId);
            actions.unlike(b, postId);
        }
        awaiter.idle();
        assertThat(notifications.count(a)).isZero();
    }

    @Test
    void 끈_종류는_만들지_않는다() throws Exception {
        notifications.mute(a, "COMMENT");
        actions.comment(b, postId, "꺼 둔 종류", null);
        actions.like(c, postId);

        awaiter.untilCount(a, 1);
        assertThat(notifications.count(a, "LIKE")).isEqualTo(1);
        assertThat(notifications.count(a, "COMMENT")).isZero();
    }

    @Test
    void 정지_회원도_받는다() throws Exception {
        members().suspend(a, Instant.now().plus(7, ChronoUnit.DAYS), "알림 시험");
        actions.comment(b, postId, "정지 회원 글에 댓글", null);
        awaiter.untilCount(a, 1);
    }
}
