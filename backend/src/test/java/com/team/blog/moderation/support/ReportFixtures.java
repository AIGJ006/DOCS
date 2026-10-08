package com.team.blog.moderation.support;

import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 신고 사건·신고 테스트 데이터. API를 거치지 않고 {@code report_case}·{@code report}를 직접 넣는다. 스냅샷은 지금 글·댓글 내용을 복사한다.
 *
 * <pre>{@code
 * long caseId = reports().pendingPost(postId, authorId);
 * reports().report(caseId, reporterId, "SPAM", null);
 * }</pre>
 */
public final class ReportFixtures {

    private final JdbcTemplate jdbc;

    public ReportFixtures(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long pendingPost(long postId, long authorId) {
        return insert("POST", postId, null, authorId, "PENDING", null, null, Instant.now());
    }

    public long pendingComment(long commentId, long authorId) {
        return insert("COMMENT", null, commentId, authorId, "PENDING", null, null, Instant.now());
    }

    /** 처리된 사건 (스냅샷 있음). */
    public long handledPost(long postId, long authorId, String status, long adminId, Instant at) {
        return insert("POST", postId, null, authorId, status, adminId, at, at);
    }

    public long insert(
            String type,
            Long postId,
            Long commentId,
            long authorId,
            String status,
            Long handledBy,
            Instant handledAt,
            Instant createdAt) {
        String title = null;
        String content = "스냅샷 내용";
        if (postId != null) {
            java.util.Map<String, Object> post =
                    jdbc.queryForMap(
                            "SELECT title, left(content_md, 2000) AS head FROM post WHERE id = ?",
                            postId);
            title = (String) post.get("title");
            content = (String) post.get("head");
        } else if (commentId != null) {
            content =
                    jdbc.queryForObject(
                            "SELECT content FROM comment WHERE id = ?", String.class, commentId);
        }
        return jdbc.queryForObject(
                "INSERT INTO report_case (target_type, post_id, comment_id, target_author_id,"
                        + " snapshot_title, snapshot_content, status, handled_by, handled_at,"
                        + " created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id",
                Long.class,
                type,
                postId,
                commentId,
                authorId,
                title,
                content,
                status,
                handledBy,
                handledAt == null ? null : Timestamp.from(handledAt),
                Timestamp.from(createdAt));
    }

    public long report(long caseId, long reporterId, String reason, String detail) {
        return report(caseId, reporterId, reason, detail, Instant.now());
    }

    public long report(long caseId, long reporterId, String reason, String detail, Instant at) {
        return jdbc.queryForObject(
                "INSERT INTO report (case_id, reporter_id, reason, detail, created_at)"
                        + " VALUES (?, ?, ?, ?, ?) RETURNING id",
                Long.class,
                caseId,
                reporterId,
                reason,
                detail,
                Timestamp.from(at));
    }

    public int caseCount() {
        return jdbc.queryForObject("SELECT count(*) FROM report_case", Integer.class);
    }

    public int reportCount() {
        return jdbc.queryForObject("SELECT count(*) FROM report", Integer.class);
    }

    public String status(long caseId) {
        return jdbc.queryForObject(
                "SELECT status FROM report_case WHERE id = ?", String.class, caseId);
    }
}
