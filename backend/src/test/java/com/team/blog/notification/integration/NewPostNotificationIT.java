package com.team.blog.notification.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.notification.application.NotificationWriter;
import com.team.blog.notification.support.NotificationActions;
import com.team.blog.notification.support.NotificationTestBase;
import com.team.blog.shared.event.PostWentPublic;
import com.team.blog.support.fixture.PostFixtures.State;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 새 글 알림 (011 T017, US1 #6, SC-012, contracts §6). 팔로우 관계는 {@code follow} 행을 직접 넣는다(010 API와 무관).
 */
class NewPostNotificationIT extends NotificationTestBase {

    @Autowired private NotificationWriter writer;

    private NotificationActions actions;
    private long author;

    @BeforeEach
    void setUp() {
        actions = new NotificationActions(mockMvc, jdbc);
        author = members().member().create();
    }

    @Test
    void 공개_발행은_팔로워_전원에게_하나씩() throws Exception {
        long[] followers = {
            members().member().create(), members().member().create(), members().member().create()
        };
        for (long f : followers) {
            actions.followRow(f, author);
        }

        long postId = actions.publish(author, "새 글", "PUBLIC");

        for (long f : followers) {
            awaiter.untilCount(f, 1);
            Map<String, Object> row = notifications.rows(f).get(0);
            assertThat(row.get("type")).isEqualTo("NEW_POST");
            assertThat(((Number) row.get("post_id")).longValue()).isEqualTo(postId);
            assertThat(((Number) row.get("last_actor_id")).longValue()).isEqualTo(author);
            assertThat(((Number) row.get("actor_count")).intValue()).isEqualTo(1);
        }
        assertThat(notifications.count(author)).isZero();
    }

    @Test
    void 비공개_발행_뒤_처음_공개될_때_한_번만() throws Exception {
        long follower = members().member().create();
        actions.followRow(follower, author);

        long postId = actions.publish(author, "처음엔 비공개", "PRIVATE");
        awaiter.idle();
        assertThat(notifications.count(follower)).isZero();

        actions.changeVisibility(author, postId, "PUBLIC");
        awaiter.untilCount(follower, 1);

        actions.changeVisibility(author, postId, "PRIVATE");
        actions.changeVisibility(author, postId, "PUBLIC");
        actions.republish(author, postId, "다시 발행", "PUBLIC");
        awaiter.idle();
        assertThat(notifications.count(follower)).isEqualTo(1);
    }

    @Test
    void 유예_팔로워와_새_글을_끈_팔로워는_빠진다() throws Exception {
        long normal = members().member().create();
        long withdrawn = members().member().create();
        long muted = members().member().create();
        for (long f : new long[] {normal, withdrawn, muted}) {
            actions.followRow(f, author);
        }
        posts.withdraw(withdrawn);
        notifications.mute(muted, "NEW_POST");

        actions.publish(author, "새 글", "PUBLIC");

        awaiter.untilCount(normal, 1);
        assertThat(notifications.count(withdrawn)).isZero();
        assertThat(notifications.count(muted)).isZero();
    }

    @Test
    void 같은_사건이_두_번_와도_한_번() {
        long follower = members().member().create();
        actions.followRow(follower, author);
        long postId = posts.create(author, State.PUBLISHED_PUBLIC);

        PostWentPublic event = new PostWentPublic(postId, author, Instant.now());
        eventPublisher.publish(event);
        eventPublisher.publish(event);

        awaiter.untilCount(follower, 1);
        awaiter.idle();
        assertThat(notifications.count(follower)).isEqualTo(1);
    }

    @Test
    @Tag("slow")
    void 팔로워_1만_명도_1초_안에() {
        jdbc.update(
                "INSERT INTO member (handle, nickname, role, status, default_visibility)"
                        + " SELECT 'np' || g, '새글' || g, 'USER', 'ACTIVE', 'PUBLIC'"
                        + " FROM generate_series(1, 10000) g");
        jdbc.update(
                "INSERT INTO follow (follower_id, followee_id)"
                        + " SELECT id, ? FROM member WHERE handle LIKE 'np%'",
                author);
        long postId = posts.create(author, State.PUBLISHED_PUBLIC);

        long started = System.nanoTime();
        int inserted = writer.addNewPost(postId, author);
        long millis = (System.nanoTime() - started) / 1_000_000;

        assertThat(inserted).isEqualTo(10_000);
        assertThat(millis).as("1만 명 INSERT … SELECT").isLessThan(1_000);
        List<Long> counts =
                jdbc.queryForList(
                        "SELECT count(*) FROM notification WHERE type = 'NEW_POST' AND post_id = ?",
                        Long.class,
                        postId);
        assertThat(counts).containsExactly(10_000L);
    }
}
