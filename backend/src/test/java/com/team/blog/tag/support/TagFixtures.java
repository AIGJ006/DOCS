package com.team.blog.tag.support;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 태그 테스트 데이터 (008). 발행 API 없이 {@code tag}·{@code post_tag}를 직접 넣는다. 이름은 정규화된 값이어야 한다(DB {@code
 * ck_tag_name}).
 *
 * <pre>{@code
 * TagFixtures tags = new TagFixtures(jdbc);
 * tags.attach(postId, "spring-boot", "jpa");   // position 0, 1 (이미 붙은 태그 뒤에 이어 붙임)
 * long id = tags.tagId("c#");                   // 없으면 만든다
 * }</pre>
 */
public final class TagFixtures {

    private final JdbcTemplate jdbc;

    public TagFixtures(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 태그 번호 (없으면 만든다). */
    public long tagId(String name) {
        jdbc.update("INSERT INTO tag (name) VALUES (?) ON CONFLICT (name) DO NOTHING", name);
        return jdbc.queryForObject("SELECT id FROM tag WHERE name = ?", Long.class, name);
    }

    /** 글에 태그를 이어 붙인다 (position은 지금 붙은 수부터). */
    public void attach(long postId, String... names) {
        Integer next =
                jdbc.queryForObject(
                        "SELECT COALESCE(MAX(position) + 1, 0) FROM post_tag WHERE post_id = ?",
                        Integer.class,
                        postId);
        int position = next == null ? 0 : next;
        for (String name : names) {
            jdbc.update(
                    "INSERT INTO post_tag (post_id, tag_id, position) VALUES (?, ?, ?)",
                    postId,
                    tagId(name),
                    position++);
        }
    }

    /** 글의 태그 이름 (position 순서). */
    public java.util.List<String> namesOf(long postId) {
        return jdbc.queryForList(
                "SELECT t.name FROM post_tag pt JOIN tag t ON t.id = pt.tag_id"
                        + " WHERE pt.post_id = ? ORDER BY pt.position",
                String.class,
                postId);
    }

    /** 그 이름의 태그 행 수. */
    public long countByName(String name) {
        return jdbc.queryForObject("SELECT count(*) FROM tag WHERE name = ?", Long.class, name);
    }
}
