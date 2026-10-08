package com.team.blog.post.infra;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 글 숨김·스냅샷 SQL (014 T011, contracts/moderation-sql.md §6). 숨김은 {@code hidden_*} 세 칸만 바꾼다 — {@code
 * updated_at}·카운터·{@code first_public_at}은 그대로라 해제하면 목록 위치가 돌아온다(SC-006).
 */
@Repository
public class PostModerationRepository {

    private final JdbcClient jdbc;

    public PostModerationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 스냅샷용 행 (휴지통 글 포함). 원문은 앞 {@code maxChars} 글자(PostgreSQL {@code left}는 코드 포인트 단위라 이모지를 가르지
     * 않는다)만 읽는다.
     */
    public Optional<SnapshotRow> findSnapshot(long postId, int maxChars) {
        return jdbc.sql(
                        """
                        SELECT id, author_id, title, left(content_md, :max) AS head,
                               hidden_at IS NOT NULL AS hidden, deleted_at IS NOT NULL AS trashed
                          FROM post WHERE id = :id
                        """)
                .param("id", postId)
                .param("max", maxChars)
                .query(
                        (rs, n) ->
                                new SnapshotRow(
                                        rs.getLong("id"),
                                        rs.getLong("author_id"),
                                        rs.getString("title"),
                                        rs.getString("head"),
                                        rs.getBoolean("hidden"),
                                        rs.getBoolean("trashed")))
                .optional();
    }

    /** 숨김 판단용 잠금 ({@code FOR UPDATE}). 행이 없으면 빈 값. 값은 숨김 여부. */
    public Optional<Boolean> lockHidden(long postId) {
        return jdbc.sql("SELECT hidden_at IS NOT NULL FROM post WHERE id = :id FOR UPDATE")
                .param("id", postId)
                .query(Boolean.class)
                .optional();
    }

    public void hide(long postId, long adminId, String reason, Instant now) {
        jdbc.sql(
                        "UPDATE post SET hidden_at = :at, hidden_by = :by, hidden_reason = :reason"
                                + " WHERE id = :id")
                .param("at", Timestamp.from(now))
                .param("by", adminId)
                .param("reason", reason)
                .param("id", postId)
                .update();
    }

    public void unhide(long postId) {
        jdbc.sql(
                        "UPDATE post SET hidden_at = NULL, hidden_by = NULL, hidden_reason = NULL"
                                + " WHERE id = :id")
                .param("id", postId)
                .update();
    }

    /** 있는 글만 결과에 담는다. */
    public Map<Long, Boolean> hiddenOf(Collection<Long> postIds) {
        Map<Long, Boolean> result = new HashMap<>();
        if (postIds == null || postIds.isEmpty()) {
            return result;
        }
        jdbc.sql("SELECT id, hidden_at IS NOT NULL AS hidden FROM post WHERE id IN (:ids)")
                .param("ids", postIds)
                .query(
                        (org.springframework.jdbc.core.RowCallbackHandler)
                                rs -> result.put(rs.getLong("id"), rs.getBoolean("hidden")));
        return result;
    }

    /**
     * @param head 원문 앞부분
     * @param trashed 휴지통 글인가
     */
    public record SnapshotRow(
            long id, long authorId, String title, String head, boolean hidden, boolean trashed) {}
}
