package com.team.blog.notification.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.notification.support.NotificationActions;
import com.team.blog.notification.support.NotificationTestBase;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 새 팔로워 묶음 (011 T034, SC-003, US3 #5). 8일 뒤는 {@code MutableClock}이 없어 묶음에 들어간 시각({@code
 * notification_actor.created_at})을 8일 전으로 옮겨 확인한다.
 */
class FollowGroupingIT extends NotificationTestBase {

    private NotificationActions actions;
    private long a;
    private long b;

    @BeforeEach
    void setUp() {
        actions = new NotificationActions(mockMvc, jdbc);
        a = members().member().create();
        b = members().member().create();
    }

    @Test
    void 팔로우는_새_팔로워_알림() throws Exception {
        actions.follow(b, a);
        awaiter.untilCount(a, 1);
        Map<String, Object> row = notifications.rows(a).get(0);
        assertThat(row.get("type")).isEqualTo("FOLLOW");
        assertThat(row.get("group_key")).isEqualTo("FOLLOW");
        assertThat(row.get("post_id")).isNull();
        assertThat(((Number) row.get("last_actor_id")).longValue()).isEqualTo(b);
    }

    @Test
    void 칠일_안_언팔로우_팔로우_반복은_늘지_않는다() throws Exception {
        actions.follow(b, a);
        awaiter.untilCount(a, 1);
        // 읽어 두어도 7일 안이면 새 묶음을 만들지 않는다
        jdbc.update("UPDATE notification SET read_at = now() WHERE receiver_id = ?", a);
        for (int i = 0; i < 3; i++) {
            actions.unfollow(b, a);
            actions.follow(b, a);
        }
        awaiter.idle();
        assertThat(notifications.count(a)).isEqualTo(1);
    }

    @Test
    void 팔일_뒤_다시_팔로우하면_다시_들어간다() throws Exception {
        actions.follow(b, a);
        awaiter.untilCount(a, 1);
        jdbc.update("UPDATE notification SET read_at = now() WHERE receiver_id = ?", a);
        jdbc.update(
                "UPDATE notification_actor SET created_at = ? WHERE actor_id = ?",
                Timestamp.from(Instant.now().minus(8, ChronoUnit.DAYS)),
                b);

        actions.unfollow(b, a);
        actions.follow(b, a);

        awaiter.untilCount(a, 2);
    }

    @Test
    void 언팔로우만으로는_알림이_없고_안_읽은_묶음에서_빠진다() throws Exception {
        long c = members().member().create();
        actions.follow(b, a);
        actions.follow(c, a);
        awaiter.idle();
        assertThat(notifications.count(a)).isEqualTo(1);
        long id = ((Number) notifications.rows(a).get(0).get("id")).longValue();
        assertThat(notifications.actors(id)).hasSize(2);

        actions.unfollow(c, a);
        awaiter.idle();
        assertThat(notifications.actors(id)).containsExactly(b);
        assertThat(notifications.count(c)).isZero();

        actions.unfollow(b, a);
        awaiter.idle();
        assertThat(notifications.count(a)).isZero();
    }
}
