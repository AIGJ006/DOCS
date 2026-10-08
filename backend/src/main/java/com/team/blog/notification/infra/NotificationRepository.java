package com.team.blog.notification.infra;

import com.team.blog.notification.domain.GroupKey;
import com.team.blog.notification.domain.NotificationType;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 알림 저장·묶음·취소·읽음·삭제·정리 SQL (011 contracts/notification-sql.md). {@link JdbcClient}만 쓴다. 모든 메서드는 호출한
 * 쪽 트랜잭션 안에서 돈다.
 */
@Repository
public class NotificationRepository {

    private final JdbcClient jdbc;

    public NotificationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 하나짜리 알림 (contracts §3). {@code group_key}는 NULL.
     *
     * @return 새 알림 번호
     */
    public long insertSingle(
            long receiverId,
            NotificationType type,
            Long postId,
            Long commentId,
            Long reportId,
            String result,
            Long actorId,
            int actorCount,
            Instant now) {
        return jdbc.sql(
                        """
                        INSERT INTO notification (receiver_id, type, post_id, comment_id, report_id, result,
                                                  last_actor_id, actor_count, group_key, created_at, updated_at)
                        VALUES (:receiverId, :type, :postId, :commentId, :reportId, :result,
                                :actorId, :actorCount, NULL, :now, :now)
                        RETURNING id
                        """)
                .param("receiverId", receiverId)
                .param("type", type.name())
                .param("postId", postId, Types.BIGINT)
                .param("commentId", commentId, Types.BIGINT)
                .param("reportId", reportId, Types.BIGINT)
                .param("result", result, Types.VARCHAR)
                .param("actorId", actorId, Types.BIGINT)
                .param("actorCount", actorCount)
                .param("now", Timestamp.from(now))
                .query(Long.class)
                .single();
    }

    /** 안 읽은 수 ({@code ix_notification_unread}). */
    public long countUnread(long receiverId) {
        return jdbc.sql(
                        "SELECT count(*) FROM notification WHERE receiver_id = :me AND read_at IS NULL")
                .param("me", receiverId)
                .query(Long.class)
                .single();
    }

    /** 그 댓글의 {@code COMMENT}·{@code REPLY} 알림 삭제 (contracts §7-1). 숨김 알림은 남긴다. */
    public int deleteCommentNotifications(long commentId) {
        return jdbc.sql(
                        "DELETE FROM notification WHERE comment_id = :c AND type IN ('COMMENT', 'REPLY')")
                .param("c", commentId)
                .update();
    }

    // ---- 묶음 저장 (contracts §4, LIKE·FOLLOW) ----

    /**
     * ① 그 사람이 그 묶음 키의 알림에 이미 들어간 적이 있는가 (읽음·안 읽음 모두, 보관 기간 안).
     *
     * @param since {@code null}이 아니면 이 시각 뒤에 들어간 것만 본다 (FOLLOW 7일)
     */
    public boolean existsActor(long receiverId, GroupKey groupKey, long actorId, Instant since) {
        String sql =
                """
                SELECT EXISTS (
                  SELECT 1 FROM notification_actor na
                    JOIN notification n ON n.id = na.notification_id
                   WHERE na.actor_id = :actorId AND n.receiver_id = :receiverId
                     AND n.group_key = :groupKey
                """
                        + (since == null ? "" : "     AND na.created_at > :since\n")
                        + ")";
        var spec =
                jdbc.sql(sql)
                        .param("actorId", actorId)
                        .param("receiverId", receiverId)
                        .param("groupKey", groupKey.value());
        if (since != null) {
            spec = spec.param("since", Timestamp.from(since));
        }
        return Boolean.TRUE.equals(spec.query(Boolean.class).single());
    }

    /** ② 안 읽은 묶음을 확보하고 그 행을 잠근다 (새로 만들든 기존 것이든). */
    public long upsertUnreadGroup(
            long receiverId, NotificationType type, Long postId, GroupKey groupKey, Instant now) {
        return jdbc.sql(
                        """
                        INSERT INTO notification (receiver_id, type, post_id, group_key, actor_count,
                                                  created_at, updated_at)
                        VALUES (:receiverId, :type, :postId, :groupKey, 0, :now, :now)
                        ON CONFLICT (receiver_id, group_key) WHERE read_at IS NULL AND group_key IS NOT NULL
                        DO UPDATE SET updated_at = notification.updated_at
                        RETURNING id
                        """)
                .param("receiverId", receiverId)
                .param("type", type.name())
                .param("postId", postId, Types.BIGINT)
                .param("groupKey", groupKey.value())
                .param("now", Timestamp.from(now))
                .query(Long.class)
                .single();
    }

    /** ③ 묶음에 사람 넣기. 이미 있으면 {@code false}. */
    public boolean insertActor(long notificationId, long actorId, Instant now) {
        return !jdbc.sql(
                        """
                        INSERT INTO notification_actor (notification_id, actor_id, created_at)
                        VALUES (:id, :actorId, :now)
                        ON CONFLICT DO NOTHING
                        RETURNING actor_id
                        """)
                .param("id", notificationId)
                .param("actorId", actorId)
                .param("now", Timestamp.from(now))
                .query(Long.class)
                .list()
                .isEmpty();
    }

    /** ④ 인원 +1, 마지막 행동자, 갱신 시각 (목록 맨 위로). */
    public void bumpGroup(long notificationId, long actorId, Instant now) {
        jdbc.sql(
                        """
                        UPDATE notification
                           SET actor_count = actor_count + 1, last_actor_id = :actorId, updated_at = :now
                         WHERE id = :id
                        """)
                .param("id", notificationId)
                .param("actorId", actorId)
                .param("now", Timestamp.from(now))
                .update();
    }

    /** 0명 묶음 지우기. */
    public int deleteIfEmpty(long notificationId) {
        return jdbc.sql("DELETE FROM notification WHERE id = :id AND actor_count = 0")
                .param("id", notificationId)
                .update();
    }

    // ---- 묶음에서 빼기 (contracts §5, PostUnliked·MemberUnfollowed) ----

    /**
     * 안 읽은 묶음에서 그 사람을 빼고 인원·마지막 행동자를 다시 계산한다({@code updated_at}은 그대로). 0명이 되면 지운다. 잠금 순서는 저장과 같다(알림
     * 행 → 사람 행). 읽은 묶음은 그대로 둔다.
     *
     * @return 실제로 뺐으면 {@code true}
     */
    public boolean removeFromUnreadGroup(long receiverId, GroupKey groupKey, long actorId) {
        List<Long> ids =
                jdbc.sql(
                                """
                                SELECT id FROM notification
                                 WHERE receiver_id = :r AND group_key = :gk AND read_at IS NULL
                                 FOR UPDATE
                                """)
                        .param("r", receiverId)
                        .param("gk", groupKey.value())
                        .query(Long.class)
                        .list();
        if (ids.isEmpty()) {
            return false;
        }
        long id = ids.get(0);
        int removed =
                jdbc.sql(
                                "DELETE FROM notification_actor WHERE notification_id = :id AND actor_id = :a")
                        .param("id", id)
                        .param("a", actorId)
                        .update();
        if (removed == 0) {
            return false;
        }
        recount(List.of(id));
        jdbc.sql("DELETE FROM notification WHERE id = :id AND actor_count = 0")
                .param("id", id)
                .update();
        return true;
    }

    /** 묶음들의 인원·마지막 행동자를 {@code notification_actor}로 다시 계산 ({@code updated_at}은 그대로). */
    public void recount(List<Long> ids) {
        if (ids.isEmpty()) {
            return;
        }
        jdbc.sql(
                        """
                        UPDATE notification n
                           SET actor_count = (SELECT count(*) FROM notification_actor x
                                               WHERE x.notification_id = n.id),
                               last_actor_id = (SELECT x.actor_id FROM notification_actor x
                                                 WHERE x.notification_id = n.id
                                                 ORDER BY x.created_at DESC, x.actor_id DESC LIMIT 1)
                         WHERE n.id IN (:ids)
                        """)
                .param("ids", ids)
                .update();
    }

    // ---- 새 글 (contracts §6) ----

    /**
     * 작성자의 팔로워 전원에게 {@code NEW_POST} 한 문장 (탈퇴 유예·끈 팔로워 제외, 같은 글 두 번 방지). plan Complexity Tracking
     * 2행 — {@code follow}·{@code member}를 읽기 전용으로 읽는다.
     *
     * @return 넣은 행 수
     */
    public int insertNewPostForFollowers(long postId, long authorId, Instant now) {
        return jdbc.sql(
                        """
                        INSERT INTO notification (receiver_id, type, post_id, last_actor_id, actor_count,
                                                  created_at, updated_at)
                        SELECT f.follower_id, 'NEW_POST', :postId, :authorId, 1, :now, :now
                          FROM follow f
                          JOIN member r ON r.id = f.follower_id
                         WHERE f.followee_id = :authorId
                           AND r.withdrawn_at IS NULL
                           AND r.deleted_at IS NULL
                           AND NOT EXISTS (SELECT 1 FROM notification_mute m
                                            WHERE m.member_id = f.follower_id AND m.type = 'NEW_POST')
                           AND NOT EXISTS (SELECT 1 FROM notification x
                                            WHERE x.receiver_id = f.follower_id AND x.type = 'NEW_POST'
                                              AND x.post_id = :postId)
                        """)
                .param("postId", postId)
                .param("authorId", authorId)
                .param("now", Timestamp.from(now))
                .update();
    }

    // ---- 읽음·삭제 (contracts §9) ----

    /** 읽음 (이미 읽음이면 그대로). 내 알림이 아니면 {@code false}. */
    public boolean markRead(long id, long me, Instant now) {
        return !jdbc.sql(
                        """
                        UPDATE notification SET read_at = COALESCE(read_at, :now)
                         WHERE id = :id AND receiver_id = :me
                        RETURNING id
                        """)
                .param("id", id)
                .param("me", me)
                .param("now", Timestamp.from(now))
                .query(Long.class)
                .list()
                .isEmpty();
    }

    /** 지금 시각까지 갱신된 안 읽은 알림을 모두 읽음으로. */
    public int markAllRead(long me, Instant now) {
        return jdbc.sql(
                        """
                        UPDATE notification SET read_at = :now
                         WHERE receiver_id = :me AND read_at IS NULL AND updated_at <= :now
                        """)
                .param("me", me)
                .param("now", Timestamp.from(now))
                .update();
    }

    /** 내 알림 삭제. 0행이면 {@code false}. */
    public boolean delete(long id, long me) {
        return jdbc.sql("DELETE FROM notification WHERE id = :id AND receiver_id = :me")
                        .param("id", id)
                        .param("me", me)
                        .update()
                > 0;
    }

    // ---- 매일 정리 (contracts §10) ----

    /** ① 보관 기간이 지난 알림을 최대 {@code batch}개 지운다 ({@code ix_notification_cleanup}). */
    public int deleteOlderThan(Instant cutoff, int batch) {
        return jdbc.sql(
                        """
                        DELETE FROM notification
                         WHERE id IN (SELECT id FROM notification WHERE updated_at < :cutoff
                                       ORDER BY updated_at LIMIT :batch)
                        """)
                .param("cutoff", Timestamp.from(cutoff))
                .param("batch", batch)
                .update();
    }

    /** ② {@code since} 뒤에 알림을 받은 사람만 최신 {@code keep}개를 넘는 알림을 지운다. */
    public int trimPerMember(Instant since, int keep) {
        return jdbc.sql(
                        """
                        DELETE FROM notification n
                        USING (
                          SELECT r.receiver_id, k.updated_at AS cut_at, k.id AS cut_id
                            FROM (SELECT DISTINCT receiver_id FROM notification WHERE updated_at > :since) r
                            CROSS JOIN LATERAL (
                              SELECT x.updated_at, x.id FROM notification x
                               WHERE x.receiver_id = r.receiver_id
                               ORDER BY x.updated_at DESC, x.id DESC
                              OFFSET :offset LIMIT 1
                            ) k
                        ) c
                        WHERE n.receiver_id = c.receiver_id
                          AND (n.updated_at, n.id) < (c.cut_at, c.cut_id)
                        """)
                .param("since", Timestamp.from(since))
                .param("offset", keep - 1)
                .update();
    }

    // ---- 탈퇴 정리 (contracts §11) ----

    /**
     * @param received 받은 알림 삭제 수
     * @param groups 사람을 뺀 남의 묶음 수
     * @param emptied 0명이 되어 지운 묶음 수
     * @param acted 내가 행동한 하나짜리 삭제 수
     */
    public record PurgeResult(int received, int groups, int emptied, int acted) {}

    /** 탈퇴 회원의 알림 정리 ①~④. 호출한 쪽 트랜잭션 안. {@code = ANY(:ids)}는 목록을 펼치는 {@code IN (:ids)}로 쓴다(같은 뜻). */
    public PurgeResult purgeMember(long memberId) {
        int received =
                jdbc.sql("DELETE FROM notification WHERE receiver_id = :m")
                        .param("m", memberId)
                        .update();
        List<Long> ids =
                jdbc.sql(
                                """
                                SELECT id FROM notification
                                 WHERE id IN (SELECT notification_id FROM notification_actor
                                               WHERE actor_id = :m)
                                 ORDER BY id FOR UPDATE
                                """)
                        .param("m", memberId)
                        .query(Long.class)
                        .list();
        jdbc.sql("DELETE FROM notification_actor WHERE actor_id = :m")
                .param("m", memberId)
                .update();
        int emptied = 0;
        if (!ids.isEmpty()) {
            recount(ids);
            emptied =
                    jdbc.sql("DELETE FROM notification WHERE id IN (:ids) AND actor_count = 0")
                            .param("ids", ids)
                            .update();
        }
        int acted =
                jdbc.sql("DELETE FROM notification WHERE last_actor_id = :m AND group_key IS NULL")
                        .param("m", memberId)
                        .update();
        jdbc.sql("DELETE FROM notification_mute WHERE member_id = :m")
                .param("m", memberId)
                .update();
        return new PurgeResult(received, ids.size(), emptied, acted);
    }
}
