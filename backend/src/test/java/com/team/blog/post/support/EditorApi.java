package com.team.blog.post.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.jayway.jsonpath.JsonPath;
import com.team.blog.support.TestLogin;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

/**
 * 002 API 호출 도우미 (테스트 전용). 상태를 바꾸는 요청은 CSRF 쿠키·헤더를 붙이고, {@code session}이 {@code null}이면 비회원으로 보낸다.
 */
public final class EditorApi {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final MockMvc mockMvc;

    public EditorApi(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    public static String json(Object body) {
        return JSON.writeValueAsString(body);
    }

    /** 발행 요청 본문. */
    public static Map<String, Object> publishBody(
            String title, String contentMd, List<String> tags, String visibility, long base) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("contentMd", contentMd);
        body.put("tags", tags);
        body.put("visibility", visibility);
        body.put("baseVersion", base);
        return body;
    }

    /** 저장 요청 본문. */
    public static Map<String, Object> saveBody(String title, String contentMd, long base) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("contentMd", contentMd);
        body.put("baseVersion", base);
        return body;
    }

    public MvcResult createPost(Cookie session, Object body) throws Exception {
        MockHttpServletRequestBuilder request = TestLogin.withCsrf(post("/api/posts"), null);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json(body));
        }
        return perform(request, session);
    }

    public long createPostId(Cookie session) throws Exception {
        MvcResult result = createPost(session, null);
        if (result.getResponse().getStatus() != 201) {
            throw new IllegalStateException(
                    "새 글 실패: " + result.getResponse().getStatus() + " " + body(result));
        }
        return ((Number) read(result, "$.postId")).longValue();
    }

    public MvcResult workingCopy(Cookie session, long postId) throws Exception {
        return perform(get("/api/posts/{postId}/working-copy", postId), session);
    }

    public MvcResult publish(Cookie session, long postId, Object body) throws Exception {
        return publish(session, postId, body, UUID.randomUUID().toString());
    }

    public MvcResult publish(Cookie session, long postId, Object body, String idempotencyKey)
            throws Exception {
        MockHttpServletRequestBuilder request =
                TestLogin.withCsrf(post("/api/posts/{postId}/publish", postId), null)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body));
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        return perform(request, session);
    }

    public MvcResult autosave(Cookie session, long postId, Object body) throws Exception {
        return perform(
                TestLogin.withCsrf(put("/api/posts/{postId}/autosave", postId), null)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)),
                session);
    }

    public MvcResult save(Cookie session, long postId, Object body) throws Exception {
        return perform(
                TestLogin.withCsrf(put("/api/posts/{postId}/working-copy", postId), null)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)),
                session);
    }

    public MvcResult discard(Cookie session, long postId) throws Exception {
        return perform(
                TestLogin.withCsrf(delete("/api/posts/{postId}/working-copy", postId), null),
                session);
    }

    public MvcResult preview(Cookie session, String contentMd) throws Exception {
        return perform(
                TestLogin.withCsrf(post("/api/markdown/preview"), null)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("contentMd", contentMd))),
                session);
    }

    private MvcResult perform(MockHttpServletRequestBuilder request, Cookie session)
            throws Exception {
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
