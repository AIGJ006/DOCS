package com.team.blog.notification.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team.blog.account.application.purge.WithdrawalStepRegistry;
import com.team.blog.notification.application.NotificationWithdrawalPurgeStep;
import com.team.blog.notification.support.NotificationTestBase;
import com.team.blog.support.fixture.PostFixtures.State;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

/** 탈퇴 정리 order 70 (011 T053·T057, US7 #3·#4, contracts §11). */
class NotificationWithdrawalPurgeIT extends NotificationTestBase {

    @Autowired private NotificationWithdrawalPurgeStep step;
    @Autowired private WithdrawalStepRegistry registry;
    @Autowired private TransactionTemplate tx;

    private long a;
    private long b;
    private long c;
    private long d;
    private long postId;

    @BeforeEach
    void setUp() {
        a = members().member().create();
        b = members().member().create();
        c = members().member().create();
        d = members().member().create();
        postId = posts.create(a, State.PUBLISHED_PUBLIC);
    }

    @Test
    void 정리_단계_목록에_order_70으로_등록된다() {
        assertThat(registry.steps())
                .filteredOn(s -> s.order() == 70)
                .singleElement()
                .isInstanceOf(NotificationWithdrawalPurgeStep.class);
        assertThat(registry.steps())
                .noneMatch(s -> s.getClass().getSimpleName().startsWith("InterimNotification"));
    }

    @Test
    void 유예_중에는_그대로_복구_후에도_그대로() {
        long received = notifications.single(b, "NEW_POST").post(postId).actor(a).create();
        long acted = notifications.group(a, "FOLLOW", null, Instant.now(), false, b);
        posts.withdraw(b);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM notification WHERE id IN (?, ?)",
                                Long.class,
                                received,
                                acted))
                .isEqualTo(2);
        jdbc.update("UPDATE member SET status = 'ACTIVE', withdrawn_at = NULL WHERE id = ?", b);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM notification WHERE id IN (?, ?)",
                                Long.class,
                                received,
                                acted))
                .isEqualTo(2);
    }

    @Test
    void 정리하면_받은_것_보낸_것_끄기_설정이_사라지고_묶음은_다시_계산된다() {
        Instant t = Instant.now().minus(1, ChronoUnit.HOURS);
        // B가 받은 알림
        notifications.single(b, "NEW_POST").post(postId).actor(a).create();
        // B·C·D 좋아요 묶음 (B가 가장 최근) — 안 읽음
        long unread = notifications.group(a, "LIKE", postId, t, false, c, d, b);
        // 읽은 묶음에서도 빠진다
        long otherPost = posts.create(a, State.PUBLISHED_PUBLIC);
        long read = notifications.group(a, "LIKE", otherPost, t, true, d, b);
        // B 혼자였던 새 팔로워 묶음
        long alone = notifications.group(a, "FOLLOW", null, t, false, b);
        // B가 행동한 하나짜리 (댓글·새 글)
        long commentId =
                new com.team.blog.interaction.support.CommentFixtures(jdbc).on(postId, b).create();
        long commented =
                notifications
                        .single(a, "COMMENT")
                        .post(postId)
                        .comment(commentId)
                        .actor(b)
                        .create();
        long newPost = notifications.single(c, "NEW_POST").post(postId).actor(b).create();
        // 남의 알림은 그대로
        long untouched = notifications.single(c, "NEW_POST").post(postId).actor(a).create();
        notifications.mute(b, "LIKE", "FOLLOW");

        tx.executeWithoutResult(s -> step.purge(b));

        assertThat(notifications.count(b)).isZero();
        Map<String, Object> u = row(unread);
        assertThat(((Number) u.get("actor_count")).intValue()).isEqualTo(2);
        assertThat(((Number) u.get("last_actor_id")).longValue()).isEqualTo(d);
        assertThat(notifications.actors(unread)).containsExactly(c, d);
        Map<String, Object> r = row(read);
        assertThat(((Number) r.get("actor_count")).intValue()).isEqualTo(1);
        assertThat(((Number) r.get("last_actor_id")).longValue()).isEqualTo(d);
        assertThat(exists(alone)).isFalse();
        assertThat(exists(commented)).isFalse();
        assertThat(exists(newPost)).isFalse();
        assertThat(exists(untouched)).isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM notification_mute WHERE member_id = ?",
                                Long.class,
                                b))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM notification_actor WHERE actor_id = ?",
                                Long.class,
                                b))
                .isZero();

        // 멱등
        tx.executeWithoutResult(s -> step.purge(b));
        assertThat(row(unread).get("actor_count")).isEqualTo(2);
    }

    @Test
    void 트랜잭션_밖에서_부르면_예외() {
        assertThatThrownBy(() -> step.purge(b))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    private Map<String, Object> row(long id) {
        return jdbc.queryForMap("SELECT * FROM notification WHERE id = ?", id);
    }

    private boolean exists(long id) {
        return jdbc.queryForObject("SELECT count(*) FROM notification WHERE id = ?", Long.class, id)
                == 1;
    }
}
