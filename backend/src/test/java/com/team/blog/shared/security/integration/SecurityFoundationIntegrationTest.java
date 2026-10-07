package com.team.blog.shared.security.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MvcResult;

/** 보안 기반: 세션 쿠키 + CSRF + CurrentUser + 보안 헤더 + SPA 대체 경로 (T024). */
class SecurityFoundationIntegrationTest extends IntegrationTestBase {

    @Autowired FindByIndexNameSessionRepository<?> sessionRepository;

    private static final String CSP =
            "default-src 'self'; script-src 'self'; connect-src 'self' http://localhost:9000;"
                    + " img-src 'self' http://localhost:9000 data: blob:; style-src 'self' 'unsafe-inline';"
                    + " object-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'";

    // ① CSRF 토큰 쿠키 발급
    @Test
    void csrf_토큰_쿠키를_발급한다() throws Exception {
        MvcResult result =
                mockMvc.perform(get("/api/auth/csrf"))
                        .andExpect(status().isNoContent())
                        .andExpect(cookie().exists("XSRF-TOKEN"))
                        .andExpect(cookie().httpOnly("XSRF-TOKEN", false))
                        .andExpect(cookie().secure("XSRF-TOKEN", true))
                        .andExpect(cookie().sameSite("XSRF-TOKEN", "Lax"))
                        .andExpect(cookie().path("XSRF-TOKEN", "/"))
                        .andReturn();
        assertThat(result.getResponse().getCookie("XSRF-TOKEN").getValue()).isNotBlank();
    }

    @Test
    void 발급받은_토큰을_헤더로_보내면_상태를_바꾸는_요청이_통과한다() throws Exception {
        Cookie token =
                mockMvc.perform(get("/api/auth/csrf"))
                        .andReturn()
                        .getResponse()
                        .getCookie("XSRF-TOKEN");
        mockMvc.perform(
                        post("/api/__public-probe")
                                .cookie(new Cookie("XSRF-TOKEN", token.getValue()))
                                .header("X-XSRF-TOKEN", token.getValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
    }

    // ② CSRF 거부
    @Test
    void csrf_헤더가_없거나_다르면_403_CSRF_REJECTED() throws Exception {
        mockMvc.perform(post("/api/__public-probe").cookie(new Cookie("XSRF-TOKEN", "abc")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_REJECTED"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.details").isEmpty());
        mockMvc.perform(
                        post("/api/__public-probe")
                                .cookie(new Cookie("XSRF-TOKEN", "abc"))
                                .header("X-XSRF-TOKEN", "different"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_REJECTED"));
        mockMvc.perform(post("/api/__public-probe"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_REJECTED"));
    }

    // ③ 비로그인 /api/me/** → 401 JSON
    @Test
    void 비로그인으로_api_me를_부르면_401_LOGIN_REQUIRED_JSON() throws Exception {
        mockMvc.perform(get("/api/me/__probe"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(
                        content()
                                .json(
                                        "{\"code\":\"LOGIN_REQUIRED\",\"message\":\"로그인이 필요해요\",\"errors\":[],\"details\":null}",
                                        JsonCompareMode.STRICT));
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_REQUIRED"));
    }

    @Test
    void LoginRequired_애너테이션은_비로그인을_401로_막는다() throws Exception {
        mockMvc.perform(get("/api/__login-required-probe"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_REQUIRED"));
        long id = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, id);
        mockMvc.perform(get("/api/__login-required-probe").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(id));
    }

    // ④ CurrentUser는 세션의 회원 번호만
    @Test
    void CurrentUser는_세션의_회원_번호를_돌려주고_본문의_memberId는_무시한다() throws Exception {
        long me = members().member().create();
        long other = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);

        mockMvc.perform(get("/api/me/__probe").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(me));
        mockMvc.perform(
                        TestLogin.withCsrf(post("/api/me/__probe"), session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"memberId\":" + other + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(me));
        mockMvc.perform(get("/api/__public-probe").cookie(session))
                .andExpect(jsonPath("$.loggedIn").value(true))
                .andExpect(jsonPath("$.memberId").value(me));
        mockMvc.perform(get("/api/__public-probe")).andExpect(jsonPath("$.loggedIn").value(false));
    }

    // ⑤ 세션 쿠키
    @Test
    void 세션_쿠키는_SESSION_HttpOnly_Secure_Lax이고_Redis_TTL은_14일() throws Exception {
        long me = members().member().create();
        MockHttpServletResponse response =
                mockMvc.perform(TestLogin.withCsrf(post("/test/login-as/{id}", me), null))
                        .andExpect(status().isOk())
                        .andExpect(cookie().exists("SESSION"))
                        .andExpect(cookie().httpOnly("SESSION", true))
                        .andExpect(cookie().secure("SESSION", true))
                        .andExpect(cookie().sameSite("SESSION", "Lax"))
                        .andExpect(cookie().path("SESSION", "/"))
                        .andReturn()
                        .getResponse();
        String sessionId = TestLogin.sessionId(response.getCookie("SESSION"));
        Long ttl = redis.getExpire("spring:session:sessions:expires:" + sessionId);
        assertThat(ttl).isBetween(14L * 24 * 3600 - 60, 14L * 24 * 3600);
        // 주체 이름 색인은 JDK 직렬화 값이라 저장소 API로 확인한다.
        assertThat(sessionRepository.findByPrincipalName(String.valueOf(me)))
                .containsKey(sessionId);
    }

    // ⑥ 보안 헤더
    @Test
    void 모든_응답에_보안_헤더를_넣는다() throws Exception {
        List<MockHttpServletResponse> responses =
                List.of(
                        mockMvc.perform(get("/api/auth/csrf")).andReturn().getResponse(),
                        mockMvc.perform(get("/api/me/__probe")).andReturn().getResponse(),
                        mockMvc.perform(post("/api/__public-probe")).andReturn().getResponse(),
                        mockMvc.perform(get("/settings")).andReturn().getResponse(),
                        mockMvc.perform(get("/api/없는경로")).andReturn().getResponse());
        for (MockHttpServletResponse response : responses) {
            assertThat(response.getHeader("Content-Security-Policy")).isEqualTo(CSP);
            assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
            assertThat(response.getHeader("Referrer-Policy"))
                    .isEqualTo("strict-origin-when-cross-origin");
        }
    }

    // ⑦ SPA 대체 경로
    @Test
    void 화면_경로는_index_html로_넘기고_없는_API는_404_공통_본문() throws Exception {
        for (String path :
                List.of(
                        "/",
                        "/settings",
                        "/signup/social",
                        "/@kim755030",
                        "/@kim755030/posts/12")) {
            mockMvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(forwardedUrl("/index.html"));
        }
        mockMvc.perform(get("/api/없는경로"))
                .andExpect(status().isNotFound())
                .andExpect(
                        content()
                                .json(
                                        "{\"code\":\"NOT_FOUND\",\"message\":\"볼 수 없는 페이지예요\",\"errors\":[],\"details\":null}",
                                        JsonCompareMode.STRICT));
        mockMvc.perform(get("/api/auth/없는/경로")).andExpect(status().isNotFound());
        mockMvc.perform(get("/assets/없는파일.js")).andExpect(status().isNotFound());
        mockMvc.perform(get("/oauth2/없는경로")).andExpect(status().isNotFound());
        mockMvc.perform(get("/actuator/없는경로")).andExpect(status().isNotFound());
    }
}
