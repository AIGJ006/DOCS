package com.team.blog.account.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.MemberFixtures;
import com.team.blog.support.RedisOutage;
import com.team.blog.support.TestLogin;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** US5 로그인 보안 (FR-035~039, SC-003·004·010, R-12·R-13·R-23·R-33, quickstart §4-4). */
class LoginSecurityIntegrationTest extends IntegrationTestBase {

    private static final String PASSWORD = MemberFixtures.DEFAULT_PASSWORD;
    private static final String WRONG = "Wrong#2026x";

    private MockHttpServletRequestBuilder loginRequest(
            String email, String password, String redirect, String ip) {
        MockHttpServletRequestBuilder request =
                TestLogin.withCsrf(
                                post("/api/auth/login")
                                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                        .param("email", email)
                                        .param("password", password),
                                null)
                        .with(
                                r -> {
                                    r.setRemoteAddr(ip);
                                    return r;
                                });
        if (redirect != null) {
            request.param("redirect", redirect);
        }
        return request;
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(loginRequest(email, password, null, "203.0.113.30"));
    }

    private long member(String email) {
        long id = members().member().email(email).create();
        SignupRequests.agreeCurrent(jdbc, id);
        return id;
    }

    // ---- #1 계정당 5회 실패 15분 잠금 ----

    @Test
    @DisplayName("#1 같은 이메일 5회 실패 뒤 맞는 비밀번호도 429 LOGIN_TEMPORARILY_LOCKED, 미가입 이메일과 상태·본문이 같다")
    void lockAfterFiveFailures() throws Exception {
        member("lock@example.com");
        MockHttpServletResponse[] registered = new MockHttpServletResponse[6];
        MockHttpServletResponse[] unknown = new MockHttpServletResponse[6];
        for (int i = 0; i < 5; i++) {
            registered[i] = login("lock@example.com", WRONG).andReturn().getResponse();
            unknown[i] = login("ghost@example.com", WRONG).andReturn().getResponse();
        }
        registered[5] =
                login("lock@example.com", PASSWORD)
                        .andExpect(status().isTooManyRequests())
                        .andExpect(jsonPath("$.code").value("LOGIN_TEMPORARILY_LOCKED"))
                        .andExpect(jsonPath("$.message").value("잠시 후 다시 시도해 주세요(약 15분)"))
                        .andExpect(header().exists("Retry-After"))
                        .andReturn()
                        .getResponse();
        unknown[5] = login("ghost@example.com", PASSWORD).andReturn().getResponse();
        for (int i = 0; i < 6; i++) {
            assertThat(unknown[i].getStatus())
                    .as("#%d", i + 1)
                    .isEqualTo(registered[i].getStatus());
            assertThat(unknown[i].getContentAsString())
                    .as("#%d", i + 1)
                    .isEqualTo(registered[i].getContentAsString());
        }
        assertThat(registered[0].getStatus()).isEqualTo(401);
        long retryAfter = Long.parseLong(registered[5].getHeader("Retry-After"));
        assertThat(retryAfter).isBetween(14 * 60L, 15 * 60L);
    }

    @Test
    @DisplayName("성공하면 실패 횟수를 지운다")
    void successResetsCounter() throws Exception {
        member("reset@example.com");
        for (int i = 0; i < 4; i++) {
            login("reset@example.com", WRONG).andExpect(status().isUnauthorized());
        }
        login("reset@example.com", PASSWORD).andExpect(status().isOk());
        for (int i = 0; i < 4; i++) {
            login("reset@example.com", WRONG).andExpect(status().isUnauthorized());
        }
        login("reset@example.com", PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("#2 틀린 이메일·틀린 비밀번호 → 같은 401 INVALID_CREDENTIALS")
    void sameFailureForWrongEmailOrPassword() throws Exception {
        member("same@example.com");
        String wrongPassword =
                login("same@example.com", WRONG)
                        .andExpect(status().isUnauthorized())
                        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String wrongEmail =
                login("nobody@example.com", PASSWORD)
                        .andExpect(status().isUnauthorized())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(wrongEmail).isEqualTo(wrongPassword);
    }

    @Test
    @DisplayName("같은 IP 1분 21번째 로그인 → 429 TOO_MANY_REQUESTS (X-Forwarded-For를 바꿔도 같은 IP)")
    void ipRateLimitIgnoresForwardedHeader() throws Exception {
        for (int i = 0; i < 20; i++) {
            mockMvc.perform(
                            loginRequest("u" + i + "@example.com", WRONG, null, "203.0.113.77")
                                    .header("X-Forwarded-For", "198.51.100." + i))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(
                        loginRequest("u21@example.com", WRONG, null, "203.0.113.77")
                                .header("X-Forwarded-For", "198.51.100.200"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"))
                .andExpect(header().exists("Retry-After"));
        mockMvc.perform(loginRequest("u22@example.com", WRONG, null, "203.0.113.78"))
                .andExpect(status().isUnauthorized());
    }

    // ---- #3·#4 정지 ----

    @Test
    @DisplayName("#3 열린 정지 회원이 맞는 비밀번호 → 403 ACCOUNT_SUSPENDED details{endsAt, reason}, 틀리면 일반 401")
    void suspendedLogin() throws Exception {
        long id = member("sus@example.com");
        Instant endsAt = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        members().suspend(id, endsAt, "스팸");
        MockHttpServletResponse response =
                login("sus@example.com", PASSWORD)
                        .andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"))
                        .andExpect(jsonPath("$.details.endsAt").value(endsAt.toString()))
                        .andExpect(jsonPath("$.details.reason").value("스팸"))
                        .andReturn()
                        .getResponse();
        assertThat(jsonOf(response)).contains("정지된 계정이에요 (~").contains("사유: 스팸");
        Cookie session = response.getCookie(TestLogin.SESSION_COOKIE);
        if (session != null) {
            mockMvc.perform(get("/api/me").cookie(session)).andExpect(status().isUnauthorized());
        }
        login("sus@example.com", WRONG)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("영구 정지는 endsAt null, 문구에 '영구'")
    void permanentSuspension() throws Exception {
        long id = member("perm@example.com");
        members().suspend(id, null, "규정 위반");
        login("perm@example.com", PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.details.endsAt").value((Object) null))
                .andExpect(jsonPath("$.message").value("정지된 계정이에요 (영구). 사유: 규정 위반"));
    }

    @Test
    @DisplayName("#4 ends_at 지난 정지 → lifted_at 기록(lifted_by NULL), status ACTIVE, 로그인 200")
    void expiredSuspensionIsLifted() throws Exception {
        long id = member("lift@example.com");
        long suspensionId =
                members().suspend(id, Instant.now().minus(1, ChronoUnit.HOURS), "기한 지남");
        login("lift@example.com", PASSWORD).andExpect(status().isOk());
        Map<String, Object> row =
                jdbc.queryForMap(
                        "SELECT lifted_at, lifted_by FROM member_suspension WHERE id = ?",
                        suspensionId);
        assertThat(row.get("lifted_at")).isNotNull();
        assertThat(row.get("lifted_by")).isNull();
        assertThat(jdbc.queryForObject("SELECT status FROM member WHERE id = ?", String.class, id))
                .isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("#5 로그인 뒤 정지되면 그 세션의 ACCOUNT_WRITE·CONTENT_WRITE 요청은 403 ACCOUNT_SUSPENDED (H7)")
    void remainingSessionBlockedAfterSuspension() throws Exception {
        long id = member("later@example.com");
        Cookie session =
                new Cookie(
                        TestLogin.SESSION_COOKIE,
                        login("later@example.com", PASSWORD)
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getCookie(TestLogin.SESSION_COOKIE)
                                .getValue());
        members().suspend(id, Instant.now().plus(1, ChronoUnit.DAYS), "스팸");
        // ACCOUNT_WRITE: 비밀번호 변경
        mockMvc.perform(
                        TestLogin.withCsrf(
                                post("/api/me/password")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"currentPassword\":\""
                                                        + PASSWORD
                                                        + "\",\"newPassword\":\"Fresh#2026b\",\"newPasswordConfirm\":\"Fresh#2026b\"}"),
                                session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));
        // CONTENT_WRITE: 새 글(002)
        mockMvc.perform(
                        TestLogin.withCsrf(
                                post("/api/posts")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{}"),
                                session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));
    }

    // ---- #8 이동 주소 ----

    @ParameterizedTest(name = "[{index}] {0} → {1}")
    @CsvSource(
            delimiter = '|',
            value = {
                "//evil.com | /",
                "https://evil.com | /",
                "/\\evil.com | /",
                "%2F%2Fevil.com | /",
                "/%2F%2Fevil.com | /",
                "/settings?tab=1 | /settings?tab=1",
                "/@kim/posts/3 | /@kim/posts/3",
            })
    void redirectIsChecked(String redirect, String expected) throws Exception {
        member("redir@example.com");
        mockMvc.perform(loginRequest("redir@example.com", PASSWORD, redirect, "203.0.113.31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redirectTo").value(expected));
    }

    @Test
    @DisplayName("WITHDRAWN 회원 로그인 → 200 accountStatus WITHDRAWN (015 복구 화면)")
    void withdrawnLogin() throws Exception {
        long id = members().member().email("wd@example.com").status("WITHDRAWN").create();
        SignupRequests.agreeCurrent(jdbc, id);
        login("wd@example.com", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountStatus").value("WITHDRAWN"));
    }

    @Test
    @DisplayName("Redis가 멈추면 로그인 → 503 TEMPORARILY_UNAVAILABLE")
    void redisOutage() throws Exception {
        member("down@example.com");
        try (RedisOutage outage = RedisOutage.start()) {
            login("down@example.com", PASSWORD)
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("TEMPORARILY_UNAVAILABLE"))
                    .andExpect(header().string("Retry-After", "30"));
        }
    }

    private static String jsonOf(MockHttpServletResponse response) throws Exception {
        return response.getContentAsString();
    }
}
