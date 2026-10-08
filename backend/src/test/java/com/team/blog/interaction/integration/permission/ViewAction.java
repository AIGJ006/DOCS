package com.team.blog.interaction.integration.permission;

import com.team.blog.interaction.support.LikeApi;
import com.team.blog.support.permission.ActionResult;
import com.team.blog.support.permission.PermissionAction;
import jakarta.servlet.http.Cookie;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code post.view}: {@code POST /api/posts/{postId}/views} (009 T029). 볼 수 있는 글은 셌든 안 셌든 204, 볼 수
 * 없는 글은 404. 쓰기가 아니라(글 행을 바꾸지 않음) 하네스의 글 스냅샷 비교 대상이 아니다.
 */
@Profile("test")
@Component
public class ViewAction implements PermissionAction {

    @Override
    public String name() {
        return "post.view";
    }

    @Override
    public String owner() {
        return LikePermissionSupport.OWNER;
    }

    @Override
    public boolean isWrite() {
        return false;
    }

    @Override
    public ActionResult perform(MockMvc mockMvc, Cookie session, Long postId) throws Exception {
        return ActionResult.of(new LikeApi(mockMvc).view(session, postId == null ? 0L : postId));
    }
}
