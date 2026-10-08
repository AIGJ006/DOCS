package com.team.blog.post.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.jayway.jsonpath.JsonPath;
import com.team.blog.support.TestLogin;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 공개 범위 변경 API 호출 도우미 (004 US1, {@code PUT /api/posts/{postId}/visibility}). 상태를 바꾸는 요청이라 CSRF
 * 쿠키·헤더를 붙인다.
 */
public final class VisibilityApi {

    public static final String PATH = "/api/posts/{postId}/visibility";

    private final MockMvc mockMvc;

    public VisibilityApi(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    /** {@code {"visibility": value}}. {@code session}이 없으면 비회원. */
    public MvcResult change(Cookie session, Object postId, String value) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("visibility", value);
        return send(session, postId, json(body));
    }

    /** 본문을 그대로 보낸다 (본문 형식 오류·끼워 넣은 필드 확인용). */
    public MvcResult send(Cookie session, Object postId, String rawBody) throws Exception {
        var request =
                TestLogin.withCsrf(put(PATH, postId), session)
                        .contentType(MediaType.APPLICATION_JSON);
        if (rawBody != null) {
            request.content(rawBody);
        }
        return mockMvc.perform(request).andReturn();
    }

    /** CSRF 헤더 없이 보낸다. */
    public MvcResult changeWithoutCsrf(Cookie session, Object postId, String value)
            throws Exception {
        var request =
                put(PATH, postId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibility\":\"" + value + "\"}");
        if (session != null) {
            request.cookie(session);
        }
        return mockMvc.perform(request).andReturn();
    }

    public static String json(Map<String, ?> body) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, ?> e : body.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(e.getKey()).append("\":");
            Object v = e.getValue();
            if (v == null) {
                sb.append("null");
            } else if (v instanceof Number || v instanceof Boolean) {
                sb.append(v);
            } else {
                sb.append('"').append(v.toString().replace("\"", "\\\"")).append('"');
            }
        }
        return sb.append('}').toString();
    }

    public static int status(MvcResult result) {
        return result.getResponse().getStatus();
    }

    public static String body(MvcResult result) {
        try {
            return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    public static <T> T read(MvcResult result, String path) {
        return JsonPath.read(body(result), path);
    }

    public static String cacheControl(MvcResult result) {
        return result.getResponse().getHeader("Cache-Control");
    }
}
