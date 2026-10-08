package com.team.blog.post.infra;

import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@code post.category_id} 읽기·쓰기 (017 research R5). 이 컬럼은 JPA {@code Post}에 매핑하지 않는다 — 발행·자동 저장의
 * UPDATE가 카테고리를 덮어쓰지 않게 이 SQL만 쓴다.
 */
@Repository
public class PostCategoryRepository {

    private final JdbcClient jdbc;

    public PostCategoryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** 내 글(휴지통 제외)을 행 잠금으로 읽어 지금 카테고리를 준다. 남의 글·없는 글·휴지통 글은 비어 있다. */
    public Optional<OwnedPostCategory> lockOwned(long postId, long memberId) {
        return jdbc.sql(
                        "SELECT category_id FROM post WHERE id = :id AND author_id = :m"
                                + " AND deleted_at IS NULL FOR UPDATE")
                .param("id", postId)
                .param("m", memberId)
                .query((rs, n) -> new OwnedPostCategory(rs.getObject("category_id", Long.class)))
                .optional();
    }

    /** 내 글(휴지통 제외)의 지금 카테고리. */
    public Optional<OwnedPostCategory> findOwned(long postId, long memberId) {
        return jdbc.sql(
                        "SELECT category_id FROM post WHERE id = :id AND author_id = :m"
                                + " AND deleted_at IS NULL")
                .param("id", postId)
                .param("m", memberId)
                .query((rs, n) -> new OwnedPostCategory(rs.getObject("category_id", Long.class)))
                .optional();
    }

    /** 카테고리만 바꾼다 — {@code updated_at}·편집 버전·"수정됨"은 그대로다(017 FR-021). */
    public void updateCategory(long postId, Long categoryId) {
        jdbc.sql("UPDATE post SET category_id = CAST(:c AS bigint) WHERE id = :id")
                .param("id", postId)
                .param("c", categoryId)
                .update();
    }

    /**
     * 글의 카테고리.
     *
     * @param categoryId 분류 없음이면 {@code null}
     */
    public record OwnedPostCategory(Long categoryId) {}
}
