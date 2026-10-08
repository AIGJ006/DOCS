package com.team.blog.tag.integration.permission;

import com.team.blog.support.permission.ActionResult;
import com.team.blog.support.permission.PermissionAction;
import com.team.blog.tag.support.TagApi;
import jakarta.servlet.http.Cookie;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;

/** {@code tag.posts}: 대상 글에 붙인 태그의 {@code GET /api/tags/{name}/posts}에 그 글이 있는가 (008 T029). */
@Profile("test")
@Component
public class TagPostsAction implements PermissionAction {

    private final JdbcTemplate jdbc;

    public TagPostsAction(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String name() {
        return "tag.posts";
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
        return TagPermissionProbe.listed(
                new TagApi(mockMvc).posts(session, TagPermissionProbe.TAG, null),
                "$.items[*].id",
                postId);
    }
}
