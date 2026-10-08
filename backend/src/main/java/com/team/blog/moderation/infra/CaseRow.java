package com.team.blog.moderation.infra;

import com.team.blog.moderation.domain.CaseStatus;
import com.team.blog.shared.event.ReportTargetType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

/**
 * {@code report_case} 한 행 (data-model §1-1).
 *
 * @param postId 글 대상이면 글 번호 (글이 지워지면 {@code null})
 * @param commentId 댓글 대상이면 댓글 번호 (댓글이 지워지면 {@code null})
 */
public record CaseRow(
        long id,
        ReportTargetType targetType,
        Long postId,
        Long commentId,
        long targetAuthorId,
        String snapshotTitle,
        String snapshotContent,
        CaseStatus status,
        Long handledBy,
        Instant handledAt,
        Instant createdAt) {

    static final String COLUMNS =
            "id, target_type, post_id, comment_id, target_author_id, snapshot_title,"
                    + " snapshot_content, status, handled_by, handled_at, created_at";

    /** 대상 번호 (사라졌으면 {@code null}). */
    public Long targetId() {
        return targetType == ReportTargetType.POST ? postId : commentId;
    }

    /** 대상 FK가 모두 비었다 — 글·댓글 행이 지워졌다. */
    public boolean isOrphan() {
        return postId == null && commentId == null;
    }

    static CaseRow map(ResultSet rs, int rowNum) throws SQLException {
        return new CaseRow(
                rs.getLong("id"),
                ReportTargetType.valueOf(rs.getString("target_type")),
                rs.getObject("post_id", Long.class),
                rs.getObject("comment_id", Long.class),
                rs.getLong("target_author_id"),
                rs.getString("snapshot_title"),
                rs.getString("snapshot_content"),
                CaseStatus.valueOf(rs.getString("status")),
                rs.getObject("handled_by", Long.class),
                instant(rs.getTimestamp("handled_at")),
                instant(rs.getTimestamp("created_at")));
    }

    static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }
}
