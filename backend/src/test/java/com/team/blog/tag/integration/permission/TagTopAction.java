package com.team.blog.tag.integration.permission;

import com.team.blog.support.permission.ActionResult;
import com.team.blog.support.permission.PermissionAction;
import com.team.blog.tag.support.TagApi;
import jakarta.servlet.http.Cookie;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;

/** {@code tag.top}: 대상 글에만 붙인 태그가 {@code GET /api/tags}(전체 태그 목록)에 있는가 (008 T048). */
@Profile("test")
@Component
public class TagTopAction implements PermissionAction {

    private final JdbcTemplate jdbc;

    public TagTopAction(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String name() {
        return "tag.top";
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
                new TagApi(mockMvc).top(session), "$.items[*].name", TagPermissionProbe.TAG);
    }
}
