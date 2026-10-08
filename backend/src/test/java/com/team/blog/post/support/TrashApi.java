package com.team.blog.post.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import com.team.blog.support.TestLogin;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 006 API 호출 도우미 (테스트 전용). 상태를 바꾸는 요청에는 CSRF 쿠키·헤더를 붙인다. */
public final class TrashApi {

    private final MockMvc mockMvc;

    public TrashApi(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    /** {@code DELETE /api/posts/{postId}} — 휴지통으로 (빈 임시글은 바로 완전 삭제). */
    public MvcResult trash(Cookie session, Object postId) throws Exception {
        return mockMvc.perform(TestLogin.withCsrf(delete("/api/posts/{id}", postId), session))
                .andReturn();
    }

    /** {@code POST /api/posts/{postId}/restore}. */
    public MvcResult restore(Cookie session, Object postId) throws Exception {
        return mockMvc.perform(TestLogin.withCsrf(post("/api/posts/{id}/restore", postId), session))
                .andReturn();
    }

    /** {@code DELETE /api/posts/{postId}/permanent}. */
    public MvcResult purge(Cookie session, Object postId) throws Exception {
        return mockMvc.perform(
                        TestLogin.withCsrf(delete("/api/posts/{id}/permanent", postId), session))
                .andReturn();
    }

    /** {@code GET /api/me/posts?…} — 쿼리는 그대로 붙인다(예: {@code tab=trash&cursor=…}). */
    public MvcResult list(Cookie session, String query) throws Exception {
        MockHttpServletRequestBuilder request =
                get("/api/me/posts" + (query == null || query.isEmpty() ? "" : "?" + query));
        if (session != null) {
            request.cookie(session);
        }
        return mockMvc.perform(request).andReturn();
    }

    public static String body(MvcResult result) {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    public static <T> T read(MvcResult result, String path) {
        return JsonPath.read(body(result), path);
    }

    public static int status(MvcResult result) {
        return result.getResponse().getStatus();
    }
}
