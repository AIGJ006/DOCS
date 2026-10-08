package com.team.blog.support.permission.actions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.team.blog.support.TestLogin;
import com.team.blog.support.permission.ActionResult;
import com.team.blog.support.permission.PermissionAction;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code post.visibility} 실행기 (004 T047): {@code PUT /api/posts/{postId}/visibility}. 대상이 {@code
 * PRIVATE}면 {@code PUBLIC}으로, 그 밖(없는 글 포함)이면 {@code PRIVATE}로 바꿔 "실제로 값이 바뀌는" 요청을 보낸다 — 거부되면 하네스가
 * 요청 전후 {@code PostSnapshot}이 같은지 본다.
 */
@Profile("test")
@Component
public class SetVisibilityAction implements PermissionAction {

    private final JdbcTemplate jdbc;

    public SetVisibilityAction(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String name() {
        return "post.visibility";
    }

    @Override
    public String owner() {
        return "004";
    }

    @Override
    public ActionResult perform(MockMvc mockMvc, Cookie session, Long postId) throws Exception {
        List<String> current =
                jdbc.queryForList("SELECT visibility FROM post WHERE id = ?", String.class, postId);
        String target = current.contains("PRIVATE") ? "PUBLIC" : "PRIVATE";
        return ActionResult.of(
                mockMvc.perform(
                                TestLogin.withCsrf(
                                                put("/api/posts/{postId}/visibility", postId),
                                                session)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"visibility\":\"" + target + "\"}"))
                        .andReturn());
    }
}
