package com.team.blog.post.permission;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

import com.team.blog.support.TestLogin;
import com.team.blog.support.permission.ActionResult;
import com.team.blog.support.permission.PermissionAction;
import jakarta.servlet.http.Cookie;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 004 권한 매트릭스 실행기 {@code post.purge}: DELETE /api/posts/{postId}/permanent — 영구 삭제 (US4, T052).
 * {@code post-write.csv}의 owner 006 행을 실행한다.
 */
@Profile("test")
@Component
public class PurgePostAction implements PermissionAction {

    @Override
    public String name() {
        return "post.purge";
    }

    @Override
    public String owner() {
        return TrashPermissionOwner.OWNER;
    }

    @Override
    public ActionResult perform(MockMvc mockMvc, Cookie session, Long postId) throws Exception {
        return ActionResult.of(
                mockMvc.perform(
                                TestLogin.withCsrf(
                                        delete("/api/posts/{id}/permanent", postId), session))
                        .andReturn());
    }
}
