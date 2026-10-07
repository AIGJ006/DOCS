package com.team.blog.discovery.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 005 읽기 API·화면 경로 호출 도우미 (테스트 전용). 모두 GET이라 CSRF 토큰이 필요 없다. */
public final class ReadingApi {

    private final MockMvc mockMvc;

    public ReadingApi(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    public MvcResult getAs(Cookie session, String path, Object... args) throws Exception {
        MockHttpServletRequestBuilder request = get(path, args);
        if (session != null) {
            request.cookie(session);
        }
        return mockMvc.perform(request).andReturn();
    }

    public MvcResult home(Cookie session, String cursor) throws Exception {
        return cursor == null
                ? getAs(session, "/api/posts")
                : getAs(session, "/api/posts?cursor={c}", cursor);
    }

    public MvcResult blogPosts(Cookie session, String handle, String cursor) throws Exception {
        return cursor == null
                ? getAs(session, "/api/members/{h}/posts", handle)
                : getAs(session, "/api/members/{h}/posts?cursor={c}", handle, cursor);
    }

    public MvcResult blogHeader(Cookie session, String handle) throws Exception {
        return getAs(session, "/api/members/{h}", handle);
    }

    public MvcResult detail(Cookie session, Object postId) throws Exception {
        return getAs(session, "/api/posts/{id}", postId);
    }

    public static int status(MvcResult result) {
        return result.getResponse().getStatus();
    }

    public static String body(MvcResult result) {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    public static byte[] bytes(MvcResult result) {
        return result.getResponse().getContentAsByteArray();
    }

    public static String cacheControl(MvcResult result) {
        return result.getResponse().getHeader("Cache-Control");
    }

    public static <T> T read(MvcResult result, String jsonPath) {
        return JsonPath.read(body(result), jsonPath);
    }

    public static List<Long> ids(MvcResult result) {
        List<Number> raw = read(result, "$.items[*].id");
        return raw.stream().map(Number::longValue).toList();
    }

    public static List<String> titles(MvcResult result) {
        return read(result, "$.items[*].title");
    }

    public static String nextCursor(MvcResult result) {
        return read(result, "$.nextCursor");
    }
}
