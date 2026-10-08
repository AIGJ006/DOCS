package com.team.blog.account.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.account.application.EmailVerificationService;
import com.team.blog.account.infra.security.OAuth2LoginFailureHandler;
import com.team.blog.shared.error.TemporarilyUnavailableException;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.MemberFixtures;
import com.team.blog.support.RedisOutage;
import com.team.blog.support.TestLogin;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.servlet.http.Cookie;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Redis 장애 한 장면 (T143, FR-040, Edge Case H8, quickstart §4-9). Redis가 멈춘 동안 공개 읽기는 계속되고, 세션을 읽지 못하는
 * 로그인 회원은 비로그인으로, 새 로그인·토큰 확인·메일 발송은 503으로 거부되며, 요청 횟수 제한은 건너뛴다. Redis가 돌아오면 같은 세션 쿠키로 다시 로그인 상태다.
 * 개별 503 응답 형식(Retry-After 등)은 각 기능 테스트가 확인한다.
 */
class RedisOutageIntegrationTest extends IntegrationTestBase {

    private static final String PASSWORD = MemberFixtures.DEFAULT_PASSWORD;
    private static final Instant OLD = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired FakeSocialProvider provider;
    @Autowired EmailVerificationService emailVerificationService;
    @Autowired CircuitBreakerRegistry circuitBreakers;

    @Test
    @DisplayName("Redis 정지 중 공개 읽기 200·기존 세션 401·로그인과 토큰 확인 503·사용 가능 확인 31번 200, 복구 후 정상")
    void outageThenRecovery() throws Exception {
        long id = members().member().email("down@example.com").create();
        long unverified = members().member().emailVerified(false).create();
        SignupRequests.agreeCurrent(jdbc, id);
        Cookie session = emailLogin("down@example.com");
        mockMvc.perform(get("/api/me").cookie(session)).andExpect(status().isOk());
        redis.delete("member:active-touch:" + id);
        jdbc.update("UPDATE member SET last_active_at = ? WHERE id = ?", Timestamp.from(OLD), id);
        String socialCode = "rd-" + UUID.randomUUID();
        provider.google(socialCode, "google-down", "g-down@example.com", true, "구글", null);
        SocialStart social = socialStart();

        try (RedisOutage outage = RedisOutage.start()) {
            mockMvc.perform(get("/api/agreements/current")).andExpect(status().isOk());

            mockMvc.perform(get("/api/me").cookie(session))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("LOGIN_REQUIRED"));

            login("down@example.com", PASSWORD)
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("TEMPORARILY_UNAVAILABLE"))
                    .andExpect(jsonPath("$.message").value("잠시 후 다시 시도해 주세요"))
                    .andExpect(header().exists("Retry-After"));

            // 소셜 콜백은 브라우저 이동이라 JSON 503 대신 "잠시 후 다시 시도해 주세요" 화면으로 보낸다(R-30).
            MockHttpServletResponse callback =
                    mockMvc.perform(
                                    get("/login/oauth2/code/google")
                                            .queryParam("code", socialCode)
                                            .queryParam("state", social.state())
                                            .cookie(social.session()))
                            .andReturn()
                            .getResponse();
            assertThat(callback.getStatus()).isEqualTo(302);
            assertThat(callback.getRedirectedUrl())
                    .isEqualTo(OAuth2LoginFailureHandler.UNAVAILABLE_PAGE);

            postJson(
                            "/api/auth/email-verification/confirm",
                            "{\"token\":\"" + "A".repeat(43) + "\"}")
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("TEMPORARILY_UNAVAILABLE"));
            postJson(
                            "/api/auth/password-reset/confirm",
                            "{\"token\":\""
                                    + "A".repeat(43)
                                    + "\",\"newPassword\":\"New#Pass2026x\",\"newPasswordConfirm\":\"New#Pass2026x\"}")
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("TEMPORARILY_UNAVAILABLE"));
            postJson("/api/auth/password-reset", "{\"email\":\"down@example.com\"}")
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("TEMPORARILY_UNAVAILABLE"));
            // 재발송은 로그인이 필요해 HTTP로는 세션을 읽지 못한 401이 먼저 나온다. 세션을 읽은 뒤 Redis가 멈춘 경우는 서비스가 503.
            mockMvc.perform(TestLogin.withCsrf(post("/api/auth/email-verification"), session))
                    .andExpect(status().isUnauthorized());
            assertThatThrownBy(() -> emailVerificationService.resend(unverified))
                    .isInstanceOf(TemporarilyUnavailableException.class);

            for (int i = 0; i < 31; i++) {
                mockMvc.perform(
                                get("/api/handles/availability")
                                        .queryParam("handle", "down" + i + "zz")
                                        .with(
                                                r -> {
                                                    r.setRemoteAddr("198.51.100.77");
                                                    return r;
                                                }))
                        .andExpect(status().isOk());
            }
        }

        // 정지 중 요청은 최근 활동을 바꾸지 않았다.
        assertThat(lastActiveAt(id)).isEqualTo(OLD);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM auth_identity WHERE provider = 'GOOGLE'",
                                Integer.class))
                .isZero();

        // 복구 직후: 세션은 다시 읽힌다(같은 쿠키로 로그인 상태). 새 로그인은 RedisGuard 회로가 열려 있는 30초 동안 계속 503이다.
        mockMvc.perform(get("/api/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(id));
        login("down@example.com", PASSWORD).andExpect(status().isServiceUnavailable());

        // 30초가 지나 회로가 반열림으로 바뀐 뒤: 새 로그인·메일 발송·최근 활동 갱신이 다시 된다.
        circuitBreakers.circuitBreaker("redis").transitionToHalfOpenState();
        mockMvc.perform(get("/api/me").cookie(session)).andExpect(status().isOk());
        assertThat(lastActiveAt(id)).isAfter(OLD);
        login("down@example.com", PASSWORD).andExpect(status().isOk());
        postJson("/api/auth/password-reset", "{\"email\":\"down@example.com\"}")
                .andExpect(status().isAccepted());
    }

    private Instant lastActiveAt(long id) {
        return jdbc.queryForObject(
                        "SELECT last_active_at FROM member WHERE id = ?", Timestamp.class, id)
                .toInstant();
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(
                TestLogin.withCsrf(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("email", email)
                                .param("password", password),
                        null));
    }

    private Cookie emailLogin(String email) throws Exception {
        MockHttpServletResponse response =
                login(email, PASSWORD).andExpect(status().isOk()).andReturn().getResponse();
        Cookie cookie = response.getCookie(TestLogin.SESSION_COOKIE);
        assertThat(cookie).isNotNull();
        return new Cookie(TestLogin.SESSION_COOKIE, cookie.getValue());
    }

    private ResultActions postJson(String path, String body) throws Exception {
        return mockMvc.perform(
                TestLogin.withCsrf(
                        post(path).contentType(MediaType.APPLICATION_JSON).content(body), null));
    }

    private SocialStart socialStart() throws Exception {
        MockHttpServletResponse response =
                mockMvc.perform(get("/oauth2/authorization/google")).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(302);
        String state =
                UriComponentsBuilder.fromUriString(response.getRedirectedUrl())
                        .build()
                        .getQueryParams()
                        .getFirst("state");
        Cookie cookie = response.getCookie(TestLogin.SESSION_COOKIE);
        assertThat(cookie).isNotNull();
        return new SocialStart(
                URLDecoder.decode(state, StandardCharsets.UTF_8),
                new Cookie(TestLogin.SESSION_COOKIE, cookie.getValue()));
    }

    private record SocialStart(String state, Cookie session) {}
}
