package com.team.blog.tag.permission;

import com.team.blog.support.ai.FakeAiConfiguration.FakeAi;
import com.team.blog.support.permission.ActionResult;
import com.team.blog.support.permission.PermissionAction;
import com.team.blog.tag.support.AiSuggestApi;
import jakarta.servlet.http.Cookie;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code tag-suggest.status}: {@code GET /api/posts/{id}/tag-suggestions/status} (013 T020,
 * research R14).
 */
@Profile("test")
@Component("aiTagSuggestStatusAction")
public class TagSuggestStatusAction implements PermissionAction {

    private final JdbcTemplate jdbc;
    private final FakeAi fakeAi;

    public TagSuggestStatusAction(JdbcTemplate jdbc, FakeAi fakeAi) {
        this.jdbc = jdbc;
        this.fakeAi = fakeAi;
    }

    @Override
    public String name() {
        return "tag-suggest.status";
    }

    @Override
    public String owner() {
        return TagSuggestAction.OWNER;
    }

    @Override
    public boolean isWrite() {
        return false;
    }

    @Override
    public ActionResult perform(MockMvc mockMvc, Cookie session, Long postId) throws Exception {
        TagSuggestAction.prepare(jdbc, fakeAi, postId);
        return ActionResult.of(new AiSuggestApi(mockMvc, jdbc).status(session, postId));
    }
}
