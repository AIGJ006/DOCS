package com.team.blog.tag.integration.permission;

import com.team.blog.support.permission.ActionResult;
import com.team.blog.support.permission.PermissionAction;
import com.team.blog.tag.support.TagApi;
import jakarta.servlet.http.Cookie;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code blog.tags}: 대상 글 작성자 블로그의 {@code GET /api/members/{handle}/tags}에 대상 글에만 붙인 태그가 있는가 (008
 * T056).
 */
@Profile("test")
@Component
public class BlogTagsAction implements PermissionAction {

    private final JdbcTemplate jdbc;

    public BlogTagsAction(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String name() {
        return "blog.tags";
    }

    @Override
    public String owner() {
        return TagPermissionProbe.OWNER;
    }

    @Override
    public boolean isWrite() {
        return false;
    }

    @Override
    public ActionResult perform(MockMvc mockMvc, Cookie session, Long postId) throws Exception {
        TagPermissionProbe.attach(jdbc, postId);
        String handle =
                jdbc.queryForObject(
                        "SELECT m.handle FROM post p JOIN member m ON m.id = p.author_id WHERE p.id = ?",
                        String.class,
                        postId);
        return TagPermissionProbe.listed(
                new TagApi(mockMvc).blogTags(session, handle),
                "$.items[*].name",
                TagPermissionProbe.TAG);
    }
}
