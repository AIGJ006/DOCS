package com.team.blog.post.infra;

import com.team.blog.post.domain.PostStatus;
import com.team.blog.post.domain.Visibility;
import com.team.blog.shared.security.Viewer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 글 읽기 전용 조회 (004). {@code member} JOIN은 plan Complexity Tracking의 원칙 II 예외로 이 클래스와 {@link
 * VisibilityFilter}에만 둔다 — 읽기만 하고 account 모듈 테이블에 쓰지 않는다.
 */
@Repository
public class PostQueryRepository {

    private final JdbcClient jdbc;
    private final VisibilityFilter visibilityFilter;

    public PostQueryRepository(JdbcClient jdbc, VisibilityFilter visibilityFilter) {
        this.jdbc = jdbc;
        this.visibilityFilter = visibilityFilter;
    }

    /**
     * 블로그 글 수 (004 T044, FR-006·FR-009, 06 V-8). {@code post p JOIN member m} + {@link
     * VisibilityFilter#forViewer(Viewer, Long)}만 쓴 {@code COUNT(*)}이라 블로그 목록과 같은 글만 센다 — 작성자 본인이 봐도
     * 같은 수다(비공개·임시·휴지통·숨김 글은 세지 않음). 005 블로그 머리말·008 태그별 글 수 등 "남에게 보이는 글 수"는 조건을 따로 쓰지 말고 이 메서드(또는
     * 같은 {@code VisibilityFilter} 조건)를 쓴다. {@code ix_post_blog}를 탄다.
     */
    public long countListedByAuthor(Viewer viewer, long authorId) {
        SqlCondition condition = visibilityFilter.forViewer(viewer, authorId);
        return jdbc.sql(
                        "SELECT COUNT(*) FROM post p JOIN member m ON m.id = p.author_id WHERE "
                                + condition.sql())
                .params(condition.params())
                .query(Long.class)
                .single();
    }

    /**
     * 상세 판정용 투영. 휴지통 행도 돌려준다(판정은 {@code PostAccessPolicy}가 삭제 여부를 먼저 본다). 쿼리 1번.
     *
     * @return 글이 없으면 빈 값
     */
    public Optional<PostView> findPostView(long postId) {
        return jdbc.sql(
                        """
                        SELECT p.id, p.author_id, p.status, p.visibility,
                               p.deleted_at, p.hidden_at, m.withdrawn_at
                          FROM post p
                          JOIN member m ON m.id = p.author_id
                         WHERE p.id = ?
                        """)
                .param(postId)
                .query(PostQueryRepository::toPostView)
                .optional();
    }

    /**
     * 상세 조회 투영 (005 T034, 40 §6). 글 + 작성자 + 프로필 사진을 쿼리 한 번으로 읽고 {@code content_md}는 읽지 않는다. 휴지통 행도
     * 돌려준다(판정은 {@code PostAccessPolicy}).
     *
     * @return 글이 없으면 빈 값
     */
    public Optional<PostDetailRow> findDetailRow(long postId) {
        return jdbc.sql(
                        """
                        SELECT p.id, p.author_id, p.title, p.content_html, p.status, p.visibility,
                               p.view_count, p.like_count, p.comment_count, p.published_at,
                               p.first_public_at, p.edited_at, p.deleted_at, p.hidden_at,
                               p.hidden_reason, p.thumbnail_url, p.excerpt,
                               m.handle, m.nickname, m.bio, m.withdrawn_at,
                               COALESCE(pi.thumb_storage_key, pi.storage_key) AS profile_key,
                               p.category_id
                          FROM post p
                          JOIN member m ON m.id = p.author_id
                          LEFT JOIN image pi ON pi.uploader_id = m.id AND pi.purpose = 'PROFILE'
                               AND pi.status = 'ATTACHED' AND pi.detached_at IS NULL
                         WHERE p.id = ?
                        """)
                .param(postId)
                .query(PostQueryRepository::toDetailRow)
                .optional();
    }

    private static PostDetailRow toDetailRow(ResultSet rs, int rowNum) throws SQLException {
        return new PostDetailRow(
                rs.getLong("id"),
                rs.getLong("author_id"),
                rs.getString("title"),
                rs.getString("content_html"),
                PostStatus.valueOf(rs.getString("status")),
                Visibility.valueOf(rs.getString("visibility")),
                rs.getLong("view_count"),
                rs.getInt("like_count"),
                rs.getInt("comment_count"),
                instant(rs.getTimestamp("published_at")),
                instant(rs.getTimestamp("first_public_at")),
                instant(rs.getTimestamp("edited_at")),
                instant(rs.getTimestamp("deleted_at")),
                instant(rs.getTimestamp("hidden_at")),
                rs.getString("hidden_reason"),
                rs.getString("thumbnail_url"),
                rs.getString("excerpt"),
                rs.getString("handle"),
                rs.getString("nickname"),
                rs.getString("bio"),
                instant(rs.getTimestamp("withdrawn_at")),
                rs.getString("profile_key"),
                rs.getObject("category_id", Long.class));
    }

    private static PostView toPostView(ResultSet rs, int rowNum) throws SQLException {
        return new PostView(
                rs.getLong("id"),
                rs.getLong("author_id"),
                PostStatus.valueOf(rs.getString("status")),
                Visibility.valueOf(rs.getString("visibility")),
                instant(rs.getTimestamp("deleted_at")),
                instant(rs.getTimestamp("hidden_at")),
                instant(rs.getTimestamp("withdrawn_at")));
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
