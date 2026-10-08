package com.team.blog.interaction.support;

import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import com.team.blog.support.TestLogin;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

/** 007 댓글 API 호출 도우미 (테스트 전용). 상태를 바꾸는 요청에는 CSRF 쿠키·헤더를 붙인다. */
public final class CommentApi {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final MockMvc mockMvc;

    public CommentApi(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    /** {@code GET /api/posts/{postId}/comments?…} — 쿼리는 그대로 붙인다. */
    public MvcResult list(Cookie session, Object postId, String query) throws Exception {
        MockHttpServletRequestBuilder request =
                get(
                        "/api/posts/"
                                + postId
                                + "/comments"
                                + (query == null || query.isEmpty() ? "" : "?" + query));
        if (session != null) {
            request.cookie(session);
        }
        return mockMvc.perform(request).andReturn();
    }

    public MvcResult list(Cookie session, Object postId) throws Exception {
        return list(session, postId, null);
    }

    /** 커서로 다음 페이지. */
    public MvcResult listAfter(Cookie session, Object postId, String cursor) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/posts/{id}/comments", postId);
        if (cursor != null) {
            request.queryParam("cursor", cursor);
        }
        if (session != null) {
            request.cookie(session);
        }
        return mockMvc.perform(request).andReturn();
    }

    public MvcResult replies(Cookie session, Object rootId, String cursor) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/comments/{id}/replies", rootId);
        if (cursor != null) {
            request.queryParam("cursor", cursor);
        }
        if (session != null) {
            request.cookie(session);
        }
        return mockMvc.perform(request).andReturn();
    }

    public MvcResult create(Cookie session, Object postId, String content, Long replyTo)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content", content);
        if (replyTo != null) {
            body.put("replyToCommentId", replyTo);
        }
        return mockMvc.perform(
                        TestLogin.withCsrf(post("/api/posts/{id}/comments", postId), session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(JSON.writeValueAsString(body)))
                .andReturn();
    }

    public MvcResult create(Cookie session, Object postId, String content) throws Exception {
        return create(session, postId, content, null);
    }

    public MvcResult edit(Cookie session, Object commentId, String content) throws Exception {
        return mockMvc.perform(
                        TestLogin.withCsrf(patch("/api/comments/{id}", commentId), session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(JSON.writeValueAsString(Map.of("content", content))))
                .andReturn();
    }

    public MvcResult delete(Cookie session, Object commentId) throws Exception {
        return mockMvc.perform(TestLogin.withCsrf(MockMvcRequestBuilders.delete("/api/comments/{id}", commentId), session))
                .andReturn();
    }

    public static String body(MvcResult result) {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    public static int status(MvcResult result) {
        return result.getResponse().getStatus();
    }

    public static <T> T read(MvcResult result, String path) {
        return JsonPath.read(body(result), path);
    }

    public static long id(MvcResult result) {
        return ((Number) read(result, "$.id")).longValue();
    }
}
