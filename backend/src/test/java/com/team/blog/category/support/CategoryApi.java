package com.team.blog.category.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.jayway.jsonpath.JsonPath;
import com.team.blog.support.TestLogin;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 017 카테고리 API 호출 도우미 (테스트 전용). 상태를 바꾸는 요청에는 CSRF 쿠키·헤더를 붙인다. */
public final class CategoryApi {

    private final MockMvc mockMvc;

    public CategoryApi(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    public MvcResult myList(Cookie session) throws Exception {
        return perform(get("/api/me/categories"), session);
    }

    public MvcResult create(Cookie session, String name, Long parentId) throws Exception {
        return send(
                post("/api/me/categories"),
                session,
                "{\"name\":" + str(name) + ",\"parentId\":" + parentId + "}");
    }

    /** 만들고 번호를 돌려준다 (201이 아니면 실패). */
    public long createOk(Cookie session, String name, Long parentId) throws Exception {
        MvcResult result = create(session, name, parentId);
        if (status(result) != 201) {
            throw new IllegalStateException(status(result) + " " + body(result));
        }
        return ((Number) read(result, "$.id")).longValue();
    }

    public MvcResult update(Cookie session, Object id, String rawJson) throws Exception {
        return send(patch("/api/me/categories/{id}", id), session, rawJson);
    }

    public MvcResult delete(Cookie session, Object id) throws Exception {
        return perform(
                TestLogin.withCsrf(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                                "/api/me/categories/{id}", id),
                        session),
                null);
    }

    public MvcResult reorder(Cookie session, Long parentId, List<Long> ids) throws Exception {
        String list = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
        return send(
                put("/api/me/categories/order"),
                session,
                "{\"parentId\":" + parentId + ",\"ids\":[" + list + "]}");
    }

    public MvcResult getPostCategory(Cookie session, Object postId) throws Exception {
        return perform(get("/api/posts/{postId}/category", postId), session);
    }

    public MvcResult setPostCategory(Cookie session, Object postId, Long categoryId)
            throws Exception {
        return send(
                put("/api/posts/{postId}/category", postId),
                session,
                "{\"categoryId\":" + categoryId + "}");
    }

    public MvcResult blogCategories(Cookie session, String handle) throws Exception {
        return perform(get("/api/members/{handle}/categories", handle), session);
    }

    /** 이미 인코딩한 주소로 GET. */
    public MvcResult getRaw(Cookie session, String encodedPathAndQuery) throws Exception {
        return perform(get(URI.create(encodedPathAndQuery)), session);
    }

    private MvcResult send(MockHttpServletRequestBuilder request, Cookie session, String json)
            throws Exception {
        return mockMvc.perform(
                        TestLogin.withCsrf(request, session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json))
                .andReturn();
    }

    private MvcResult perform(MockHttpServletRequestBuilder request, Cookie session)
            throws Exception {
        if (session != null) {
            request.cookie(session);
        }
        return mockMvc.perform(request).andReturn();
    }

    private static String str(String s) {
        return s == null ? "null" : "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    public static int status(MvcResult result) {
        return result.getResponse().getStatus();
    }

    public static String body(MvcResult result) {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    public static <T> T read(MvcResult result, String jsonPath) {
        return JsonPath.read(body(result), jsonPath);
    }
}
