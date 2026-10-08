package com.team.blog.account.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.RedisOutage;
import com.team.blog.support.TestLogin;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;

/** US4 비밀번호 찾기·재설정 (FR-042~044, SC-004·005, quickstart §4-5). */
class PasswordResetIntegrationTest extends IntegrationTestBase {

    private static final String NEW_PASSWORD = "Fresh#2026b";
    private static final String MESSAGE = "가입된 이메일이면 안내 메일을 보냈어요";

    private ResultActions request(String email) throws Exception {
        return request(email, "203.0.113.10");
    }

    private ResultActions request(String email, String ip) throws Exception {
        return mockMvc.perform(
                TestLogin.withCsrf(
                                post("/api/auth/password-reset")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"email\":\"" + email + "\"}"),
                                null)
                        .with(
                                r -> {
                                    r.setRemoteAddr(ip);
                                    return r;
                                }));
    }

    private ResultActions confirm(String token, String password, String confirm) throws Exception {
        return mockMvc.perform(
                TestLogin.withCsrf(
                        post("/api/auth/password-reset/confirm")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"token\":\""
                                                + token
                                                + "\",\"newPassword\":\""
                                                + password
                                                + "\",\"newPasswordConfirm\":\""
                                                + confirm
                                                + "\"}"),
                        null));
    }

    private String passwordHash(long memberId) {
        return jdbc.queryForObject(
                "SELECT password_hash FROM auth_identity WHERE member_id = ?",
                String.class,
                memberId);
    }

    @Test
    @DisplayName("#1 가입·미가입 이메일 → 같은 202 본문, 메일은 가입 이메일에만")
    void sameResponseRegardlessOfRegistration() throws Exception {
        members().member().email("kim755030@naver.com").create();
        MockHttpServletResponse registered =
                request("kim755030@naver.com")
                        .andExpect(status().isAccepted())
                        .andReturn()
                        .getResponse();
        MockHttpServletResponse unknown =
                request("nobody@naver.com")
                        .andExpect(status().isAccepted())
                        .andReturn()
                        .getResponse();
        assertThat(registered.getContentAsString()).isEqualTo(unknown.getContentAsString());
        assertThat(registered.getContentAsString()).contains(MESSAGE);
        assertThat(registered.getContentType()).isEqualTo(unknown.getContentType());

        SignupRequests.awaitMailCount(mailSender, "kim755030@naver.com", 1);
        assertThat(mailSender.lastTextFor("kim755030@naver.com").orElseThrow())
                .contains("/reset-password?token=")
                .contains("30분");
        SignupRequests.assertMailCountStays(mailSender, "nobody@naver.com", 0);
    }

    @Test
    @DisplayName("형식이 틀린 이메일 → 400 EMAIL_INVALID_FORMAT")
    void invalidFormat() throws Exception {
        request("not-an-email")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("EMAIL_INVALID_FORMAT"));
    }

    @Test
    @DisplayName("#2 Google 계정만 있는 이메일 → 링크 없는 '비밀번호가 없어요' 메일")
    void socialOnly() throws Exception {
        members().member().provider("GOOGLE").email("social@gmail.com").create();
        request("social@gmail.com").andExpect(status().isAccepted());
        SignupRequests.awaitMailCount(mailSender, "social@gmail.com", 1);
        assertThat(mailSender.lastTextFor("social@gmail.com").orElseThrow())
                .contains("이 이메일은 Google로 가입되어 비밀번호가 없어요")
                .doesNotContain("token=");
    }

    @Test
    @DisplayName("LOCAL + 같은 이메일 GitHub 계정 → 링크 + 'GitHub로 로그인' 안내")
    void localWithSameEmailSocial() throws Exception {
        members().member().email("both@example.com").create();
        members().member().provider("GITHUB").email("both@example.com").create();
        request("both@example.com").andExpect(status().isAccepted());
        SignupRequests.awaitMailCount(mailSender, "both@example.com", 1);
        assertThat(mailSender.lastTextFor("both@example.com").orElseThrow())
                .contains("/reset-password?token=")
                .contains("GitHub로 로그인하세요");
    }

    @Test
    @DisplayName("#3 링크로 새 비밀번호 저장 → 204, 두 세션 모두 401, 같은 토큰 재사용 400")
    void confirmTerminatesAllSessions() throws Exception {
        long id = members().member().email("reset@example.com").create();
        Cookie a = TestLogin.loginAs(mockMvc, id);
        Cookie b = TestLogin.loginAs(mockMvc, id);
        mockMvc.perform(get("/api/me").cookie(a)).andExpect(status().isOk());
        String before = passwordHash(id);

        request("reset@example.com").andExpect(status().isAccepted());
        SignupRequests.awaitMailCount(mailSender, "reset@example.com", 1);
        String token = mailSender.lastTokenFor("reset@example.com").orElseThrow();

        confirm(token, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());
        assertThat(passwordHash(id)).isNotEqualTo(before);
        assertThat(new BCryptPasswordEncoder().matches(NEW_PASSWORD, passwordHash(id))).isTrue();
        mockMvc.perform(get("/api/me").cookie(a)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/me").cookie(b)).andExpect(status().isUnauthorized());

        confirm(token, NEW_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LINK_EXPIRED"));
    }

    @Test
    @DisplayName("토큰은 30분 유효, 지나면 400 LINK_EXPIRED")
    void tokenExpires() throws Exception {
        members().member().email("ttl@example.com").create();
        request("ttl@example.com").andExpect(status().isAccepted());
        SignupRequests.awaitMailCount(mailSender, "ttl@example.com", 1);
        String token = mailSender.lastTokenFor("ttl@example.com").orElseThrow();
        Long ttl = redis.getExpire("auth:reset:" + token);
        assertThat(ttl).isBetween(29 * 60L, 30 * 60L);
        redis.delete("auth:reset:" + token); // 31분이 지난 것과 같다
        confirm(token, NEW_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LINK_EXPIRED"));
    }

    @Test
    @DisplayName("규칙 위반 비밀번호 → 400 VALIDATION_FAILED, 토큰은 그대로 남아 다시 쓸 수 있다")
    void invalidPasswordKeepsToken() throws Exception {
        members().member().email("rule@example.com").create();
        request("rule@example.com").andExpect(status().isAccepted());
        SignupRequests.awaitMailCount(mailSender, "rule@example.com", 1);
        String token = mailSender.lastTokenFor("rule@example.com").orElseThrow();

        confirm(token, "short", "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("newPassword"));
        confirm(token, NEW_PASSWORD, "Other#2026c")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("newPasswordConfirm"));
        confirm(token, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("정지·탈퇴 유예 회원도 재설정할 수 있다")
    void suspendedAndWithdrawnCanReset() throws Exception {
        long suspended = members().member().email("sus@example.com").create();
        members().suspend(suspended, Instant.now().plus(3, ChronoUnit.DAYS), "스팸");
        members().member().email("wd@example.com").status("WITHDRAWN").create();
        for (String email : new String[] {"sus@example.com", "wd@example.com"}) {
            request(email).andExpect(status().isAccepted());
            SignupRequests.awaitMailCount(mailSender, email, 1);
            confirm(mailSender.lastTokenFor(email).orElseThrow(), NEW_PASSWORD, NEW_PASSWORD)
                    .andExpect(status().isNoContent());
        }
    }

    @Test
    @DisplayName("같은 이메일 1분 2번째 → 429 (가입 여부와 무관하게 같은 응답)")
    void perEmailMinuteLimit() throws Exception {
        members().member().email("rl@example.com").create();
        request("rl@example.com").andExpect(status().isAccepted());
        MockHttpServletResponse registered =
                request("rl@example.com")
                        .andExpect(status().isTooManyRequests())
                        .andExpect(header().exists("Retry-After"))
                        .andReturn()
                        .getResponse();
        request("ghost@example.com").andExpect(status().isAccepted());
        MockHttpServletResponse unknown =
                request("ghost@example.com")
                        .andExpect(status().isTooManyRequests())
                        .andReturn()
                        .getResponse();
        assertThat(registered.getContentAsString()).isEqualTo(unknown.getContentAsString());
    }

    @Test
    @DisplayName("같은 이메일 하루 11번째 → 429")
    void perEmailDailyLimit() throws Exception {
        for (int i = 0; i < 10; i++) {
            request("daily@example.com", "203.0.113." + (20 + i)).andExpect(status().isAccepted());
            redis.keys("rl:reset:email:*").forEach(redis::delete); // 1분 제한만 풀어 하루 제한을 센다
        }
        request("daily@example.com", "203.0.113.99").andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("같은 IP 1시간 21번째 → 429")
    void perIpHourlyLimit() throws Exception {
        for (int i = 0; i < 20; i++) {
            request("ip" + i + "@example.com", "198.51.100.50").andExpect(status().isAccepted());
        }
        request("ip20@example.com", "198.51.100.50")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }

    @Test
    @DisplayName("Redis가 멈추면 비밀번호 찾기·재설정 → 503")
    void redisOutage() throws Exception {
        members().member().email("down@example.com").create();
        try (RedisOutage outage = RedisOutage.start()) {
            request("down@example.com")
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("TEMPORARILY_UNAVAILABLE"));
            confirm("A".repeat(43), NEW_PASSWORD, NEW_PASSWORD)
                    .andExpect(status().isServiceUnavailable());
        }
    }
}
