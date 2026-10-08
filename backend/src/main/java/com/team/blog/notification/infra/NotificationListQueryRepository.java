package com.team.blog.notification.infra;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 알림 목록 SQL 1번 (011 research R10, contracts §8, FR-032).
 *
 * <p><b>원칙 II 읽기 예외 (plan Complexity Tracking 1행)</b>: 알림·행동한 사람·글 제목을 한 번에 가져오고 읽기 판정도 그 결과로 한꺼번에
 * 하려고 {@code member}(행동자·글 작성자·받는 사람)·{@code image}(지금 프로필 사진)·{@code post}(제목·상태·공개 범위·휴지통·숨김·사유)·
 * {@code comment}(미리보기·숨김·사유)를 읽기 전용으로 JOIN한다. 공개 조회로 채우면 페이지마다 SQL이 5번 이상이 된다.
 *
 * <p>커서 조건은 커서가 있을 때만 붙인다(JDBC NULL 타입 문제, 010 R6과 같은 이유). {@code size + 1}개를 읽어 다음 페이지 여부를 안다.
 */
@Repository
public class NotificationListQueryRepository {

    private static final String SELECT =
            """
            SELECT n.id, n.type, n.post_id, n.comment_id, n.result, n.actor_count, n.read_at, n.updated_at,
                   n.last_actor_id AS actor_id,
                   a.handle AS actor_handle, a.nickname AS actor_nickname, a.withdrawn_at AS actor_withdrawn_at,
                   a.deleted_at AS actor_deleted_at,
                   COALESCE(ai.thumb_storage_key, ai.storage_key) AS actor_profile_key,
                   p.title, p.author_id, p.status, p.visibility, p.deleted_at AS post_deleted_at,
                   p.hidden_at AS post_hidden_at, p.hidden_reason AS post_hidden_reason,
                   pm.handle AS post_author_handle, pm.withdrawn_at AS post_author_withdrawn_at,
                   left(c.content, :previewScan) AS comment_head, c.deleted_at AS comment_deleted_at,
                   c.hidden_at AS comment_hidden_at, c.hidden_reason AS comment_hidden_reason,
                   me.handle AS receiver_handle
              FROM notification n
              JOIN member me ON me.id = n.receiver_id
              LEFT JOIN member a ON a.id = n.last_actor_id
              LEFT JOIN image ai ON ai.uploader_id = a.id AND ai.purpose = 'PROFILE'
                   AND ai.status = 'ATTACHED' AND ai.detached_at IS NULL
              LEFT JOIN post p ON p.id = n.post_id
              LEFT JOIN member pm ON pm.id = p.author_id
              LEFT JOIN comment c ON c.id = n.comment_id
             WHERE n.receiver_id = :me
            """;

    private static final String ORDER = " ORDER BY n.updated_at DESC, n.id DESC LIMIT :limit";

    private final JdbcClient jdbc;

    public NotificationListQueryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @param afterUpdatedAt 커서 위치 (첫 페이지면 {@code null})
     * @param limit 읽을 행 수 ({@code size + 1})
     */
    public List<NotificationRow> page(
            long receiverId, Instant afterUpdatedAt, Long afterId, int limit, int previewScan) {
        boolean cursor = afterUpdatedAt != null && afterId != null;
        var spec =
                jdbc.sql(
                                SELECT
                                        + (cursor
                                                ? "   AND (n.updated_at, n.id) < (:t, :id)\n"
                                                : "")
                                        + ORDER)
                        .param("me", receiverId)
                        .param("previewScan", previewScan)
                        .param("limit", limit);
        if (cursor) {
            spec = spec.param("t", Timestamp.from(afterUpdatedAt)).param("id", afterId);
        }
        return spec.query(NotificationListQueryRepository::map).list();
    }

    private static NotificationRow map(ResultSet rs, int n) throws SQLException {
        return new NotificationRow(
                rs.getLong("id"),
                rs.getString("type"),
                longOrNull(rs, "post_id"),
                longOrNull(rs, "comment_id"),
                rs.getString("result"),
                rs.getInt("actor_count"),
                instant(rs, "read_at"),
                instant(rs, "updated_at"),
                longOrNull(rs, "actor_id"),
                rs.getString("actor_handle"),
                rs.getString("actor_nickname"),
                instant(rs, "actor_withdrawn_at"),
                instant(rs, "actor_deleted_at"),
                rs.getString("actor_profile_key"),
                rs.getString("title"),
                longOrNull(rs, "author_id"),
                rs.getString("status"),
                rs.getString("visibility"),
                instant(rs, "post_deleted_at"),
                instant(rs, "post_hidden_at"),
                rs.getString("post_hidden_reason"),
                rs.getString("post_author_handle"),
                instant(rs, "post_author_withdrawn_at"),
                rs.getString("comment_head"),
                instant(rs, "comment_deleted_at"),
                instant(rs, "comment_hidden_at"),
                rs.getString("comment_hidden_reason"),
                rs.getString("receiver_handle"));
    }

    private static Long longOrNull(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts == null ? null : ts.toInstant();
    }
}
