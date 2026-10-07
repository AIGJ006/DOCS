package com.team.blog.support.permission;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import jakarta.servlet.http.Cookie;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * {@code post.read} 실행기 (004): 읽기 판정 {@code PostReadService.requireReadable}을 테스트 컨트롤러 {@code GET
 * /api/__test/posts/{postId}}로 부른다. 005의 상세 API·화면 행동({@code read-detail-api}·{@code
 * read-detail-page})은 005가 따로 등록한다.
 */
@Profile("test")
@Component
public class ReadPostAction implements PermissionAction {

    @Override
    public String name() {
        return "post.read";
    }

    @Override
    public String owner() {
        return "004";
    }

    @Override
    public boolean isWrite() {
        return false;
    }

    @Override
    public ActionResult perform(MockMvc mockMvc, Cookie session, Long postId) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/__test/posts/{postId}", postId);
        if (session != null) {
            request.cookie(session);
        }
        return ActionResult.of(mockMvc.perform(request).andReturn());
    }
}
