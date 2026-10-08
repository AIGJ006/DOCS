package com.team.blog.interaction.integration.permission;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

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

/** {@code comment.update} 실행기 (007 T038): 대상 댓글은 MEMBER 행위자 계정으로 SQL 삽입(research R13). */
@Profile("test")
@Component
public class EditCommentAction implements PermissionAction {

    private final CommentPermissionSupport support;

    public EditCommentAction(CommentPermissionSupport support) {
        this.support = support;
    }

    @Override
    public String name() {
        return "comment.update";
    }

    @Override
    public String owner() {
        return "007";
    }

    @Override
    public ActionResult perform(MockMvc mockMvc, Cookie session, Long postId) throws Exception {
        long commentId =
                support.postExists(postId)
                        ? support.arrangeTarget(mockMvc, session, postId)
                        : CommentPermissionSupport.NONEXISTENT_COMMENT;
        List<Map<String, Object>> before = support.snapshot(postId);
        return support.checked(
                mockMvc.perform(
                                TestLogin.withCsrf(patch("/api/comments/{id}", commentId), session)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"content\":\"고친 내용\"}"))
                        .andReturn(),
                postId,
                before);
    }
}
