package com.team.blog.interaction.integration.permission;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.team.blog.support.permission.ActionResult;
import com.team.blog.support.permission.PermissionAction;
import jakarta.servlet.http.Cookie;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** {@code comment.list} 실행기 (007 T017): {@code GET /api/posts/{postId}/comments}. */
@Profile("test")
@Component
public class ListCommentsAction implements PermissionAction {

    @Override
    public String name() {
        return "comment.list";
    }

    @Override
    public String owner() {
        return "007";
    }

    @Override
    public boolean isWrite() {
        return false;
    }

    @Override
    public ActionResult perform(MockMvc mockMvc, Cookie session, Long postId) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/posts/{postId}/comments", postId);
        if (session != null) {
            request.cookie(session);
        }
        return ActionResult.of(mockMvc.perform(request).andReturn());
    }
}
