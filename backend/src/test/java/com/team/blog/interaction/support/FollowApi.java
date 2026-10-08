package com.team.blog.interaction.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.jayway.jsonpath.JsonPath;
import com.team.blog.support.TestLogin;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 팔로우·피드(010) API 호출 도우미 (테스트 전용). 상태를 바꾸는 요청에는 CSRF 쿠키·헤더를 붙인다. */
public final class FollowApi {

    private final MockMvc mockMvc;

    public FollowApi(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    public MvcResult follow(Cookie session, String handle) throws Exception {
        return mockMvc.perform(TestLogin.withCsrf(put("/api/members/{h}/follow", handle), session))
                .andReturn();
    }

    public MvcResult unfollow(Cookie session, String handle) throws Exception {
        return mockMvc.perform(
                        TestLogin.withCsrf(delete("/api/members/{h}/follow", handle), session))
                .andReturn();
    }

    public MvcResult followers(Cookie session, String handle, String cursor) throws Exception {
        return list("/api/members/{h}/followers", session, handle, cursor);
    }

    public MvcResult following(Cookie session, String handle, String cursor) throws Exception {
        return list("/api/members/{h}/following", session, handle, cursor);
    }

    public MvcResult feed(Cookie session, String cursor) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/feed");
        if (cursor != null) {
            request.param("cursor", cursor);
        }
        if (session != null) {
            request.cookie(session);
        }
        return mockMvc.perform(request).andReturn();
    }

    public MvcResult header(Cookie session, String handle) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/members/{h}", handle);
        if (session != null) {
            request.cookie(session);
        }
        return mockMvc.perform(request).andReturn();
    }

    private MvcResult list(String path, Cookie session, String handle, String cursor)
            throws Exception {
        MockHttpServletRequestBuilder request = get(path, handle);
        if (cursor != null) {
            request.param("cursor", cursor);
        }
        if (session != null) {
            request.cookie(session);
        }
        return mockMvc.perform(request).andReturn();
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
