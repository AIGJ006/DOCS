package com.team.blog.notification.infra;

import com.team.blog.notification.domain.GroupKey;
import com.team.blog.notification.domain.NotificationType;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
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
}
