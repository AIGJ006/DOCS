package com.team.blog.post.permission;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.team.blog.support.TestLogin;
import com.team.blog.support.permission.ActionResult;
import com.team.blog.support.permission.PermissionAction;
import jakarta.servlet.http.Cookie;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 004 권한 매트릭스 실행기 {@code post.restore}: POST /api/posts/{postId}/restore — 휴지통에서 복구 (US2, T026).
 * {@code post-write.csv}의 owner 006 행을 실행한다.
 */
@Profile("test")
@Component
public class RestorePostAction implements PermissionAction {

    @Override
    public String name() {
        return "post.restore";
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
                                        post("/api/posts/{id}/restore", postId), session))
                        .andReturn());
    }
}
