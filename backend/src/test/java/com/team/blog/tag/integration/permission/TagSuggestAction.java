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
 * {@code tag.suggest}: 대상 글 태그 이름의 앞 두 글자로 {@code GET /api/tags/suggest}를 불러 그 태그가 후보에 있는가 (008
 * T042).
 */
@Profile("test")
@Component
public class TagSuggestAction implements PermissionAction {

    private final JdbcTemplate jdbc;

    public TagSuggestAction(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String name() {
        return "tag.suggest";
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
                new TagApi(mockMvc).suggest(session, TagPermissionProbe.TAG.substring(0, 2)),
                "$[*].name",
                TagPermissionProbe.TAG);
    }
}
