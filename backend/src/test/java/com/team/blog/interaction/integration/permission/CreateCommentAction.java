package com.team.blog.interaction.integration.permission;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.team.blog.support.TestLogin;
import com.team.blog.support.permission.ActionResult;
import com.team.blog.support.permission.PermissionAction;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;

/** {@code comment.create} 실행기 (007 T029). 거부되면 그 글의 댓글 행이 전후 같아야 한다. */
@Profile("test")
@Component
public class CreateCommentAction implements PermissionAction {

    private final CommentPermissionSupport support;

    public CreateCommentAction(CommentPermissionSupport support) {
        this.support = support;
    }

    @Override
    public String name() {
        return "comment.create";
    }

    @Override
    public String owner() {
        return "007";
    }

    @Override
    public ActionResult perform(MockMvc mockMvc, Cookie session, Long postId) throws Exception {
        List<Map<String, Object>> before = support.snapshot(postId);
        return support.checked(
                mockMvc.perform(
                                TestLogin.withCsrf(
                                                post("/api/posts/{id}/comments", postId), session)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"content\":\"권한 시험\"}"))
                        .andReturn(),
                postId,
                before);
    }
}
