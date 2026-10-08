package com.team.blog.category.support;

import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;

/** 카테고리 직접 INSERT (API를 거치지 않는 준비용). */
public final class CategoryFixtures {

    private final JdbcTemplate jdbc;

    public CategoryFixtures(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long create(long memberId, Long parentId, String name) {
        Integer position =
                jdbc.queryForObject(
                        "SELECT COALESCE(max(position) + 1, 0) FROM category WHERE member_id = ?"
                                + " AND COALESCE(parent_id, 0) = ?",
                        Integer.class,
                        memberId,
                        parentId == null ? 0L : parentId);
        return jdbc.queryForObject(
                "INSERT INTO category (member_id, parent_id, name, name_key, position)"
                        + " VALUES (?, ?, ?, ?, ?) RETURNING id",
                Long.class,
                memberId,
                parentId,
                name,
                name.toLowerCase(Locale.ROOT),
                position);
    }

    public void assign(long postId, Long categoryId) {
        jdbc.update("UPDATE post SET category_id = ? WHERE id = ?", categoryId, postId);
    }

    public Long categoryOf(long postId) {
        return jdbc.queryForObject("SELECT category_id FROM post WHERE id = ?", Long.class, postId);
    }

    public String handleOf(long memberId) {
        return jdbc.queryForObject(
                "SELECT handle FROM member WHERE id = ?", String.class, memberId);
    }
}
