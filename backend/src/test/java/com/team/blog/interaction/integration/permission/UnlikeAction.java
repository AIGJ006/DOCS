package com.team.blog.interaction.integration.permission;

import com.team.blog.interaction.support.LikeApi;
import com.team.blog.support.permission.ActionResult;
import com.team.blog.support.permission.PermissionAction;
import jakarta.servlet.http.Cookie;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;

/** {@code post.unlike}: {@code DELETE /api/posts/{postId}/like} (009 T019). 거부되면 좋아요 수·행 수 그대로. */
@Profile("test")
@Component
public class UnlikeAction implements PermissionAction {

    private final JdbcTemplate jdbc;

    public UnlikeAction(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String name() {
        return "post.unlike";
    }

    @Override
    public String owner() {
        return LikePermissionSupport.OWNER;
    }

    @Override
    public ActionResult perform(MockMvc mockMvc, Cookie session, Long postId) throws Exception {
        LikePermissionSupport.seedOtherLike(jdbc, postId);
        Object target = postId == null ? 0L : postId;
        return LikePermissionSupport.performChecked(
                jdbc, postId, () -> ActionResult.of(new LikeApi(mockMvc).unlike(session, target)));
    }
}
