package com.team.blog.discovery.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 012 트렌딩·검색 API 호출 도우미 (테스트 전용). 모두 GET이라 CSRF 토큰이 필요 없다. */
public final class SearchApi {

    private final MockMvc mockMvc;
    private Cookie[] cookies = new Cookie[0];
    private String remoteAddr;
    private String userAgent;

    public SearchApi(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    /** 이 쿠키(세션·vid)를 붙인다. */
    public SearchApi as(Cookie... cookies) {
        SearchApi api = copy();
        api.cookies = cookies == null ? new Cookie[0] : cookies;
        return api;
    }

    /** 요청 IP·User-Agent를 정한다 (쿠키 없는 비회원 방문자 키). */
    public SearchApi from(String remoteAddr, String userAgent) {
        SearchApi api = copy();
        api.remoteAddr = remoteAddr;
        api.userAgent = userAgent;
        return api;
    }

    private SearchApi copy() {
        SearchApi api = new SearchApi(mockMvc);
        api.cookies = cookies;
        api.remoteAddr = remoteAddr;
        api.userAgent = userAgent;
        return api;
    }

    public MvcResult posts(String q) throws Exception {
        return posts(q, null, null, null);
    }

    public MvcResult posts(String q, String sort, String cursor, String blog) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/search/posts");
        if (q != null) {
            request.queryParam("q", q);
        }
        if (sort != null) {
            request.queryParam("sort", sort);
        }
        if (cursor != null) {
            request.queryParam("cursor", cursor);
        }
        if (blog != null) {
            request.queryParam("blog", blog);
        }
        return perform(request);
    }

    public MvcResult people(String q) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/search/people");
        if (q != null) {
            request.queryParam("q", q);
        }
        return perform(request);
    }

    public MvcResult trending(String cursor) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/posts/trending");
        if (cursor != null) {
            request.queryParam("cursor", cursor);
        }
        return perform(request);
    }

    private MvcResult perform(MockHttpServletRequestBuilder request) throws Exception {
        if (cookies.length > 0) {
            request.cookie(cookies);
        }
        if (remoteAddr != null) {
            String addr = remoteAddr;
            request.with(
                    r -> {
                        r.setRemoteAddr(addr);
                        return r;
                    });
        }
        if (userAgent != null) {
            request.header("User-Agent", userAgent);
        }
        return mockMvc.perform(request).andReturn();
    }

    /** 끝까지 넘겨 모든 글 번호를 모은다 (빈 페이지가 있으면 실패). */
    public List<Long> allPostIds(String q, String sort, String blog) throws Exception {
        List<Long> seen = new ArrayList<>();
        String cursor = null;
        int guard = 0;
        do {
            MvcResult page = posts(q, sort, cursor, blog);
            if (status(page) != 200) {
                throw new AssertionError("검색 실패 " + status(page) + " " + body(page));
            }
            List<Long> ids = ids(page);
            if (ids.isEmpty() && guard > 0) {
                throw new AssertionError("빈 페이지");
            }
            seen.addAll(ids);
            cursor = read(page, "$.nextCursor");
            if (++guard > 50) {
                throw new AssertionError("끝나지 않음");
            }
        } while (cursor != null);
        return seen;
    }

    public static int status(MvcResult result) {
        return result.getResponse().getStatus();
    }

    public static String body(MvcResult result) {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    public static <T> T read(MvcResult result, String path) {
        return JsonPath.read(body(result), path);
    }

    public static List<Long> ids(MvcResult result) {
        List<Number> ids = read(result, "$.items[*].id");
        return ids.stream().map(Number::longValue).toList();
    }
}
