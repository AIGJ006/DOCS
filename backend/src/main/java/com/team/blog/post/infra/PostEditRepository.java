package com.team.blog.post.infra;

import com.team.blog.post.domain.PostStatus;
import com.team.blog.post.domain.ServerCopy;
import com.team.blog.post.domain.Visibility;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 에디터·저장용 네이티브 SQL (002 T049·T077). 소유 확인 조회는 {@code author_id = :me AND deleted_at IS NULL}(휴지통
 * 404), 1분 반영·수동 저장의 반영 쿼리는 {@code deleted_at} 조건을 넣지 않는다(B-3 ⑥).
 */
@Repository
public class PostEditRepository {

    private final JdbcClient jdbc;

    public PostEditRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 에디터·저장 판정용 상태 (PK 1회, 잠금 없음). 남의 글·휴지통 글·없는 글은 빈 값.
     *
     * @param postStatus 글 상태
     * @param visibility 공개 범위
     * @param postVersion {@code post.edit_version}
     * @param draftVersion {@code post_draft.edit_version} (작업본 없으면 {@code null})
     */
    public record EditState(
            long postId,
            PostStatus postStatus,
            Visibility visibility,
            String postTitle,
            String postContentMd,
            long postVersion,
            Instant postUpdatedAt,
            String draftTitle,
            String draftContentMd,
            Long draftVersion,
            Instant draftUpdatedAt) {

        public boolean hasDraft() {
            return draftVersion != null;
        }

        public boolean isPublished() {
            return postStatus == PostStatus.PUBLISHED;
        }

        /** DB 현재 버전 = max(post, post_draft). */
        public long dbVersion() {
            return draftVersion == null ? postVersion : Math.max(postVersion, draftVersion);
        }

        /** DB에서 가장 새 내용 (작업본이 있으면 작업본). */
        public ServerCopy dbCopy() {
            if (draftVersion != null && draftVersion >= postVersion) {
                return new ServerCopy(draftTitle, draftContentMd, draftVersion, draftUpdatedAt);
            }
            return new ServerCopy(postTitle, postContentMd, postVersion, postUpdatedAt);
        }
    }

    public Optional<EditState> findOwnedEditState(long postId, long memberId) {
        return jdbc.sql(
                        """
                        SELECT p.id, p.status, p.visibility, p.title, p.content_md, p.edit_version,
                               p.updated_at, d.title AS d_title, d.content_md AS d_content_md,
                               d.edit_version AS d_version, d.updated_at AS d_updated_at
                          FROM post p
                          LEFT JOIN post_draft d ON d.post_id = p.id
                         WHERE p.id = :id AND p.author_id = :me AND p.deleted_at IS NULL
                        """)
                .param("id", postId)
                .param("me", memberId)
                .query(PostEditRepository::toEditState)
                .optional();
    }

    /** 내 글(휴지통 아님)인가. */
    public boolean isOwned(long postId, long memberId) {
        return jdbc.sql(
                                "SELECT count(*) FROM post"
                                        + " WHERE id = :id AND author_id = :me AND deleted_at IS NULL")
                        .param("id", postId)
                        .param("me", memberId)
                        .query(Long.class)
                        .single()
                > 0;
    }

    /** 작성자 주소 ({@code member.handle}) — 글 주소 {@code /@{handle}/posts/{id}}용. */
    public Optional<String> findAuthorHandle(long memberId) {
        return jdbc.sql("SELECT handle FROM member WHERE id = ?")
                .param(memberId)
                .query(String.class)
                .optional();
    }

    private static EditState toEditState(ResultSet rs, int n) throws SQLException {
        long draftVersion = rs.getLong("d_version");
        boolean hasDraft = !rs.wasNull();
        return new EditState(
                rs.getLong("id"),
                PostStatus.valueOf(rs.getString("status")),
                Visibility.valueOf(rs.getString("visibility")),
                rs.getString("title"),
                rs.getString("content_md"),
                rs.getLong("edit_version"),
                instant(rs.getTimestamp("updated_at")),
                rs.getString("d_title"),
                rs.getString("d_content_md"),
                hasDraft ? draftVersion : null,
                instant(rs.getTimestamp("d_updated_at")));
    }

    static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
