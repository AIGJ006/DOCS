package com.team.blog.interaction.infra;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;

/**
 * {@code comment} 한 행 (007 data-model §1-1).
 *
 * @param parentId 최상위면 {@code null}, 답글이면 항상 최상위 번호
 * @param replyToMemberId 답글의 답글일 때 대상 회원
 * @param deletedAt "삭제된 자리" 시각
 * @param hiddenAt 관리자 숨김 시각
 */
public record CommentRow(
        long id,
        long postId,
        long authorId,
        Long parentId,
        Long replyToMemberId,
        String content,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt,
        Instant hiddenAt) {

    /** SELECT 목록 (별칭 없이 {@code comment} 테이블 컬럼). */
    public static final String COLUMNS =
            "id, post_id, author_id, parent_id, reply_to_member_id, content, created_at,"
                    + " updated_at, deleted_at, hidden_at";

    public boolean isRoot() {
        return parentId == null;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isHidden() {
        return hiddenAt != null;
    }

    /** 이 댓글의 최상위 번호 (최상위면 자기 번호). */
    public long rootId() {
        return parentId != null ? parentId : id;
    }

    static CommentRow map(ResultSet rs, int rowNum) throws SQLException {
        return new CommentRow(
                rs.getLong("id"),
                rs.getLong("post_id"),
                rs.getLong("author_id"),
                (Long) rs.getObject("parent_id", Long.class),
                (Long) rs.getObject("reply_to_member_id", Long.class),
                rs.getString("content"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"),
                instant(rs, "deleted_at"),
                instant(rs, "hidden_at"));
    }

    static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
