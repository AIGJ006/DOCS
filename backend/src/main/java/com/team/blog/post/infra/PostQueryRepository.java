package com.team.blog.post.infra;

import com.team.blog.post.domain.PostStatus;
import com.team.blog.post.domain.Visibility;
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

    public PostQueryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
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
