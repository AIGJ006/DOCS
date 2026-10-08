package com.team.blog.interaction.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.jayway.jsonpath.JsonPath;
import com.team.blog.support.TestLogin;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 009 좋아요·조회 기록 API 호출 도우미 (테스트 전용). 상태를 바꾸는 요청이라 CSRF 쿠키·헤더를 붙인다. */
public final class LikeApi {

    private final MockMvc mockMvc;

    public LikeApi(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    public MvcResult like(Cookie session, Object postId) throws Exception {
        return mockMvc.perform(TestLogin.withCsrf(put("/api/posts/{id}/like", postId), session))
                .andReturn();
    }

    public MvcResult unlike(Cookie session, Object postId) throws Exception {
        return mockMvc.perform(TestLogin.withCsrf(delete("/api/posts/{id}/like", postId), session))
                .andReturn();
    }

    /** 조회 기록. {@code headers}는 User-Agent·Sec-Purpose 등, {@code cookies}는 vid 등. */
    public MvcResult view(
            Cookie session, Object postId, Map<String, String> headers, Cookie... cookies)
            throws Exception {
        MockHttpServletRequestBuilder request =
                TestLogin.withCsrf(post("/api/posts/{id}/views", postId), session);
        headers.forEach(request::header);
        if (cookies.length > 0) {
            request.cookie(cookies);
        }
        return mockMvc.perform(request).andReturn();
    }

    public MvcResult view(Cookie session, Object postId) throws Exception {
        return view(session, postId, Map.of());
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
