package com.team.blog.tag.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.util.UriUtils;

/**
 * 008 태그 API·화면 경로 호출 도우미 (테스트 전용). 주소는 이미 인코딩한 문자열을 그대로 보낸다({@link URI}) — 실제 브라우저처럼 {@code
 * %23}·{@code +}·한글 인코딩이 Security 필터 체인({@code StrictHttpFirewall})을 거친다.
 */
public final class TagApi {

    private final MockMvc mockMvc;

    public TagApi(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    /** 경로 조각 인코딩 (서버 링크와 같은 {@code UriUtils.encodePathSegment}). */
    public static String segment(String name) {
        return UriUtils.encodePathSegment(name, StandardCharsets.UTF_8);
    }

    /** 이미 인코딩한 주소로 GET. */
    public MvcResult getRaw(Cookie session, String encodedPathAndQuery) throws Exception {
        MockHttpServletRequestBuilder request = get(URI.create(encodedPathAndQuery));
        if (session != null) {
            request.cookie(session);
        }
        return mockMvc.perform(request).andReturn();
    }

    public MvcResult summary(Cookie session, String name) throws Exception {
        return getRaw(session, "/api/tags/" + segment(name) + "/summary");
    }

    public MvcResult posts(Cookie session, String name, String cursor) throws Exception {
        String path = "/api/tags/" + segment(name) + "/posts";
        return getRaw(
                session,
                cursor == null
                        ? path
                        : path
                                + "?cursor="
                                + UriUtils.encodeQueryParam(cursor, StandardCharsets.UTF_8));
    }

    public MvcResult top(Cookie session) throws Exception {
        return getRaw(session, "/api/tags");
    }

    public MvcResult suggest(Cookie session, String q) throws Exception {
        return getRaw(
                session,
                "/api/tags/suggest?q=" + UriUtils.encodeQueryParam(q, StandardCharsets.UTF_8));
    }

    public MvcResult blogTags(Cookie session, String handle) throws Exception {
        return getRaw(session, "/api/members/" + segment(handle) + "/tags");
    }

    public MvcResult blogPosts(Cookie session, String handle, String tag, String cursor)
            throws Exception {
        StringBuilder uri = new StringBuilder("/api/members/" + segment(handle) + "/posts");
        String sep = "?";
        if (tag != null) {
            uri.append(sep).append("tag=").append(UriUtils.encode(tag, StandardCharsets.UTF_8));
            sep = "&";
        }
        if (cursor != null) {
            uri.append(sep)
                    .append("cursor=")
                    .append(UriUtils.encodeQueryParam(cursor, StandardCharsets.UTF_8));
        }
        return getRaw(session, uri.toString());
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

    public static <T> T read(MvcResult result, String jsonPath) {
        return JsonPath.read(body(result), jsonPath);
    }

    public static List<Long> ids(MvcResult result) {
        List<Number> raw = read(result, "$.items[*].id");
        return raw.stream().map(Number::longValue).toList();
    }
}
