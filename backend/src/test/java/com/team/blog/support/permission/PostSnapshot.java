package com.team.blog.support.permission;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 요청 전후 비교용 글 스냅샷 (42 §12 #1, research R-28): {@code title, content_md, status, visibility,
 * edit_version, updated_at, edited_at, first_public_at}에 휴지통·숨김 여부({@code deleted_at}, {@code
 * hidden_at})와 작업본({@code post_draft}의 제목·본문·버전)을 더해 비교한다.
 */
public record PostSnapshot(Map<String, Object> post, Map<String, Object> draft) {

    private static final String POST_COLUMNS =
            "title, content_md, status, visibility, edit_version, updated_at, edited_at,"
                    + " first_public_at, deleted_at, hidden_at";

    /** 글이 없으면 빈 값. */
    public static Optional<PostSnapshot> take(JdbcTemplate jdbc, long postId) {
        List<Map<String, Object>> rows =
                jdbc.queryForList("SELECT " + POST_COLUMNS + " FROM post WHERE id = ?", postId);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        List<Map<String, Object>> drafts =
                jdbc.queryForList(
                        "SELECT title, content_md, edit_version FROM post_draft WHERE post_id = ?",
                        postId);
        return Optional.of(
                new PostSnapshot(normalize(rows.get(0)), drafts.isEmpty() ? null : drafts.get(0)));
    }

    private static Map<String, Object> normalize(Map<String, Object> row) {
        Map<String, Object> copy = new java.util.LinkedHashMap<>();
        row.forEach((k, v) -> copy.put(k, v instanceof Timestamp t ? t.toInstant() : v));
        return copy;
    }
}
