package com.team.blog.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 테스트 로그인 도우미. {@code POST /test/login-as/{memberId}}(test 프로필 전용, {@link TestLoginController})로 실제
 * Spring Session(Redis) 세션을 만들고 {@code SESSION} 쿠키를 돌려준다.
 *
 * <pre>{@code
 * Cookie session = TestLogin.loginAs(mockMvc, memberId);
 * mockMvc.perform(TestLogin.withCsrf(post("/api/posts"), session)).andExpect(...);
 * mockMvc.perform(get("/api/me").cookie(session)).andExpect(...);
 * }</pre>
 */
public final class TestLogin {

    public static final String SESSION_COOKIE = "SESSION";
    public static final String CSRF_COOKIE = "XSRF-TOKEN";
    public static final String CSRF_HEADER = "X-XSRF-TOKEN";

    private TestLogin() {}

    /** 그 회원으로 로그인한 세션 쿠키. */
    public static Cookie loginAs(MockMvc mockMvc, long memberId) {
        try {
            MvcResult result =
                    mockMvc.perform(withCsrf(post("/test/login-as/{memberId}", memberId), null))
                            .andReturn();
            if (result.getResponse().getStatus() != 200) {
                throw new IllegalStateException(
                        "테스트 로그인 실패: "
                                + result.getResponse().getStatus()
                                + " "
                                + result.getResponse().getContentAsString());
            }
            Cookie cookie = result.getResponse().getCookie(SESSION_COOKIE);
            if (cookie == null) {
                throw new IllegalStateException("SESSION 쿠키가 없습니다");
            }
            return new Cookie(SESSION_COOKIE, cookie.getValue());
        } catch (Exception e) {
            throw e instanceof RuntimeException re ? re : new IllegalStateException(e);
        }
    }

    /**
     * 상태를 바꾸는 요청에 CSRF 쿠키·헤더(같은 값)를 붙인다. {@code session}이 있으면 세션 쿠키도 붙인다.
     * (CookieCsrfTokenRepository + SPA 처리기: 헤더 값 = 쿠키 값)
     */
    public static MockHttpServletRequestBuilder withCsrf(
            MockHttpServletRequestBuilder request, Cookie session) {
        String token = UUID.randomUUID().toString();
        request.cookie(new Cookie(CSRF_COOKIE, token)).header(CSRF_HEADER, token);
        if (session != null) {
            request.cookie(session);
        }
        return request;
    }

    /** 세션 쿠키 값(Base64)에서 세션 ID를 꺼낸다. */
    public static String sessionId(Cookie session) {
        return new String(
                java.util.Base64.getDecoder().decode(session.getValue()),
                java.nio.charset.StandardCharsets.UTF_8);
    }
}
