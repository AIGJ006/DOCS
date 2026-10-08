package com.team.blog.notification.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.notification.application.NotificationWriter;
import com.team.blog.notification.support.NotificationActions;
import com.team.blog.notification.support.NotificationTestBase;
import com.team.blog.support.fixture.PostFixtures.State;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 좋아요 묶음 (011 T033, SC-002, US3 #1~#4·#6, contracts §4·§5). */
class LikeGroupingIT extends NotificationTestBase {

    @Autowired private NotificationWriter writer;

    private NotificationActions actions;
    private long a;
    private long postId;

    @BeforeEach
    void setUp() {
        actions = new NotificationActions(mockMvc, jdbc);
        a = members().member().create();
        postId = posts.create(a, State.PUBLISHED_PUBLIC);
    }

    @Test
    void 열_명_동시_좋아요는_묶음_하나_인원_10() throws Exception {
        List<Long> likers = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            long m = members().member().create();
            actions.likeRow(postId, m);
            likers.add(m);
        }
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (long m : likers) {
                results.add(
                        pool.submit(
                                () -> {
                                    go.await();
                                    return writer.addLike(a, m, postId);
                                }));
            }
            go.countDown();
            for (Future<Boolean> f : results) {
                assertThat(f.get(10, TimeUnit.SECONDS)).isTrue();
            }
        } finally {
            pool.shutdownNow();
        }
        List<Map<String, Object>> rows = notifications.rows(a);
        assertThat(rows).hasSize(1);
        Map<String, Object> row = rows.get(0);
        assertThat(row.get("type")).isEqualTo("LIKE");
        assertThat(row.get("group_key")).isEqualTo("LIKE:post:" + postId);
        assertThat(((Number) row.get("actor_count")).intValue()).isEqualTo(10);
        long id = ((Number) row.get("id")).longValue();
        assertThat(notifications.actors(id))
                .hasSize(10)
                .containsExactlyInAnyOrderElementsOf(likers);
    }

    @Test
    void 취소_재클릭_다섯_번은_늘지_않는다() throws Exception {
        long b = members().member().create();
        actions.like(b, postId);
        awaiter.untilCount(a, 1);
        for (int i = 0; i < 5; i++) {
            actions.unlike(b, postId);
            actions.like(b, postId);
        }
        awaiter.idle();
        List<Map<String, Object>> rows = notifications.rows(a);
        assertThat(rows).hasSize(1);
        assertThat(((Number) rows.get(0).get("actor_count")).intValue()).isEqualTo(1);
    }

    @Test
    void 취소하면_빠지고_마지막_행동자를_다시_계산한다() throws Exception {
        long b = members().member().create();
        long c = members().member().create();
        Instant t = Instant.now().minus(1, ChronoUnit.HOURS);
        actions.likeRow(postId, b);
        actions.likeRow(postId, c);
        long id = notifications.group(a, "LIKE", postId, t, false, b, c);
        Object updatedBefore = updatedAt(id);

        actions.unlike(c, postId);
        awaiter.idle();

        Map<String, Object> row = notifications.rows(a).get(0);
        assertThat(((Number) row.get("actor_count")).intValue()).isEqualTo(1);
        assertThat(((Number) row.get("last_actor_id")).longValue()).isEqualTo(b);
        assertThat(updatedAt(id)).isEqualTo(updatedBefore);

        actions.unlike(b, postId);
        awaiter.idle();
        assertThat(notifications.count(a)).isZero();
    }

    @Test
    void 읽은_묶음은_그대로_새_좋아요는_새_묶음() throws Exception {
        long b = members().member().create();
        long c = members().member().create();
        actions.likeRow(postId, b);
        long readGroup =
                notifications.group(a, "LIKE", postId, Instant.now().minusSeconds(60), true, b);

        actions.unlike(b, postId);
        awaiter.idle();
        assertThat(notifications.actors(readGroup)).containsExactly(b);

        actions.like(c, postId);
        awaiter.untilCount(a, 2);
        Map<String, Object> fresh = notifications.rows(a).get(1);
        assertThat(fresh.get("read_at")).isNull();
        assertThat(((Number) fresh.get("last_actor_id")).longValue()).isEqualTo(c);
    }

    @Test
    void 사람이_더해지면_목록_맨_위로() throws Exception {
        long b = members().member().create();
        long c = members().member().create();
        actions.likeRow(postId, b);
        long group =
                notifications.group(
                        a, "LIKE", postId, Instant.now().minus(2, ChronoUnit.HOURS), false, b);
        long newer =
                notifications
                        .single(a, "NEW_POST")
                        .post(postId)
                        .actor(b)
                        .at(Instant.now().minus(1, ChronoUnit.HOURS))
                        .create();

        actions.like(c, postId);
        awaiter.idle();

        List<Long> order =
                jdbc.queryForList(
                        "SELECT id FROM notification WHERE receiver_id = ? ORDER BY updated_at DESC, id DESC",
                        Long.class,
                        a);
        assertThat(order).containsExactly(group, newer);
        assertThat(notifications.actors(group)).containsExactly(b, c);
    }

    private Object updatedAt(long id) {
        return jdbc.queryForObject(
                "SELECT updated_at FROM notification WHERE id = ?", Object.class, id);
    }
}
