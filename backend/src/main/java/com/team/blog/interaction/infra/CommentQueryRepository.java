package com.team.blog.interaction.infra;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 댓글 목록 SQL (007 T014, research R8·R11). 페이지당 SQL은 최상위 1번 + 답글 미리보기 1번이다(회원 표시·사진은 각 모듈 공개 Service가
 * 1번씩 — 합해서 4번). 최상위는 {@code ix_comment_root}, 답글은 {@code ix_comment_reply}를 쓴다. 정렬은 항상 {@code
 * (created_at, id)} 오름차순이다.
 */
@Repository
public class CommentQueryRepository {

    private static final String SELECT = "SELECT " + CommentRow.COLUMNS + " FROM comment";

    private final JdbcClient jdbc;

    public CommentQueryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** 최상위 댓글을 기준 다음부터 {@code limit}개 (기준이 {@code null}이면 처음부터). */
    public List<CommentRow> findRoots(long postId, Instant afterAt, Long afterId, int limit) {
        String cursor =
                afterAt == null ? "" : " AND (created_at, id) > (:t, :cid)";
        JdbcClient.StatementSpec spec =
                jdbc.sql(
                                SELECT
                                        + " WHERE post_id = :postId AND parent_id IS NULL"
                                        + cursor
                                        + " ORDER BY created_at, id LIMIT :limit")
                        .param("postId", postId)
                        .param("limit", limit);
        if (afterAt != null) {
            spec = spec.param("t", afterAt.atOffset(ZoneOffset.UTC)).param("cid", afterId);
        }
        return spec.query(CommentRow::map).list();
    }

    /** 최상위 댓글 중 기준({@code (t, id)})과 같거나 뒤인 것부터 {@code limit}개 — 바로 가기의 첫 위치. */
    public List<CommentRow> findRootsFrom(long postId, Instant at, long id, int limit) {
        return jdbc.sql(
                        SELECT
                                + " WHERE post_id = :postId AND parent_id IS NULL AND (created_at,"
                                + " id) >= (:t, :cid) ORDER BY created_at, id LIMIT :limit")
                .param("postId", postId)
                .param("t", at.atOffset(ZoneOffset.UTC))
                .param("cid", id)
                .param("limit", limit)
                .query(CommentRow::map)
                .list();
    }

    /** 기준 앞의 최상위 댓글 {@code limit}개 (오래된 순으로 돌려준다). */
    public List<CommentRow> findRootsBefore(long postId, Instant at, long id, int limit) {
        List<CommentRow> rows =
                new ArrayList<>(
                        jdbc.sql(
                                        SELECT
                                                + " WHERE post_id = :postId AND parent_id IS NULL"
                                                + " AND (created_at, id) < (:t, :cid) ORDER BY"
                                                + " created_at DESC, id DESC LIMIT :limit")
                                .param("postId", postId)
                                .param("t", at.atOffset(ZoneOffset.UTC))
                                .param("cid", id)
                                .param("limit", limit)
                                .query(CommentRow::map)
                                .list());
        java.util.Collections.reverse(rows);
        return rows;
    }

    /** 기준 앞에 최상위 댓글이 있는가. */
    public boolean hasRootsBefore(long postId, Instant at, long id) {
        return jdbc.sql(
                        "SELECT EXISTS (SELECT 1 FROM comment WHERE post_id = :postId AND parent_id"
                                + " IS NULL AND (created_at, id) < (:t, :cid))")
                .param("postId", postId)
                .param("t", at.atOffset(ZoneOffset.UTC))
                .param("cid", id)
                .query(Boolean.class)
                .single();
    }

    /**
     * 최상위들의 처음 답글 {@code preview}개와 답글 수 (SQL 1번, {@code unnest} + {@code LATERAL … LIMIT}). 답글이 없는
     * 최상위는 결과에 없다.
     */
    public Map<Long, Replies> findReplyPreviews(Collection<Long> rootIds, int preview) {
        Map<Long, Replies> result = new LinkedHashMap<>();
        if (rootIds.isEmpty()) {
            return result;
        }
        jdbc.sql(
                        """
                        SELECT r.root_id, cnt.n, c.id, c.post_id, c.author_id, c.parent_id,
                               c.reply_to_member_id, c.content, c.created_at, c.updated_at,
                               c.deleted_at, c.hidden_at
                          FROM unnest(CAST(:ids AS bigint[])) AS r(root_id)
                          CROSS JOIN LATERAL (
                                SELECT count(*) AS n FROM comment WHERE parent_id = r.root_id) cnt
                          CROSS JOIN LATERAL (
                                SELECT * FROM comment
                                 WHERE parent_id = r.root_id
                                 ORDER BY created_at, id
                                 LIMIT :preview) c
                         ORDER BY r.root_id, c.created_at, c.id
                        """)
                .param("ids", arrayLiteral(rootIds))
                .param("preview", preview)
                .query(
                        rs -> {
                            long rootId = rs.getLong("root_id");
                            int total = rs.getInt("n");
                            Replies replies =
                                    result.computeIfAbsent(
                                            rootId, k -> new Replies(total, new ArrayList<>()));
                            replies.rows().add(CommentRow.map(rs, 0));
                        });
        return result;
    }

    /** 답글을 기준 다음부터 {@code limit}개. */
    public List<CommentRow> findReplies(long rootId, Instant afterAt, Long afterId, int limit) {
        String cursor = afterAt == null ? "" : " AND (created_at, id) > (:t, :cid)";
        JdbcClient.StatementSpec spec =
                jdbc.sql(
                                SELECT
                                        + " WHERE parent_id = :rootId"
                                        + cursor
                                        + " ORDER BY created_at, id LIMIT :limit")
                        .param("rootId", rootId)
                        .param("limit", limit);
        if (afterAt != null) {
            spec = spec.param("t", afterAt.atOffset(ZoneOffset.UTC)).param("cid", afterId);
        }
        return spec.query(CommentRow::map).list();
    }

    /** 바로 가기 대상: 그 글의 댓글 한 행. */
    public Optional<CommentRow> findForAround(long commentId, long postId) {
        return jdbc.sql(SELECT + " WHERE id = :id AND post_id = :postId")
                .param("id", commentId)
                .param("postId", postId)
                .query(CommentRow::map)
                .optional();
    }

    /** 번호로 한 행 (글 무관). */
    public Optional<CommentRow> find(long commentId) {
        return jdbc.sql(SELECT + " WHERE id = :id")
                .param("id", commentId)
                .query(CommentRow::map)
                .optional();
    }

    /** 답글 수. */
    public int countReplies(long rootId) {
        return jdbc.sql("SELECT count(*) FROM comment WHERE parent_id = :id")
                .param("id", rootId)
                .query(Integer.class)
                .single();
    }

    static String arrayLiteral(Collection<Long> ids) {
        StringBuilder out = new StringBuilder("{");
        for (Long id : ids) {
            if (out.length() > 1) {
                out.append(',');
            }
            out.append(id);
        }
        return out.append('}').toString();
    }

    /**
     * 최상위 하나의 답글 미리보기.
     *
     * @param total 그 최상위 아래 답글 수 (숨김·삭제 상태 포함)
     * @param rows 처음 답글들 (오래된 순)
     */
    public record Replies(int total, List<CommentRow> rows) {}
}
