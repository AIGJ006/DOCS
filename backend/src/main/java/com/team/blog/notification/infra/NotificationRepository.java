package com.team.blog.notification.infra;

import com.team.blog.notification.domain.NotificationType;
import java.sql.Timestamp;
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
                .param("postId", postId, java.sql.Types.BIGINT)
                .param("commentId", commentId, java.sql.Types.BIGINT)
                .param("reportId", reportId, java.sql.Types.BIGINT)
                .param("result", result, java.sql.Types.VARCHAR)
                .param("actorId", actorId, java.sql.Types.BIGINT)
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
}
