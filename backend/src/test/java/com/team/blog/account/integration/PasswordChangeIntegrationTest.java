package com.team.blog.account.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.MemberFixtures;
import com.team.blog.support.TestLogin;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.ResultActions;

/** US4 로그인 상태 비밀번호 변경 (FR-045, quickstart §4-5). */
class PasswordChangeIntegrationTest extends IntegrationTestBase {

    private static final String CURRENT = MemberFixtures.DEFAULT_PASSWORD;
    private static final String NEW_PASSWORD = "Fresh#2026b";

    private ResultActions change(Cookie session, String current, String next, String confirm)
            throws Exception {
        return mockMvc.perform(
                TestLogin.withCsrf(
                        post("/api/me/password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"currentPassword\":\""
                                                + current
                                                + "\",\"newPassword\":\""
                                                + next
                                                + "\",\"newPasswordConfirm\":\""
                                                + confirm
                                                + "\"}"),
                        session));
    }

    @Test
    @DisplayName("#4 A에서 변경 → 204, B는 401, A는 그대로(세션 ID 새로), 알림 메일")
    void changeKeepsCurrentSession() throws Exception {
        long id = members().member().email("chg@example.com").create();
        Cookie a = TestLogin.loginAs(mockMvc, id);
        Cookie b = TestLogin.loginAs(mockMvc, id);

        MockHttpServletResponse response =
                change(a, CURRENT, NEW_PASSWORD, NEW_PASSWORD)
                        .andExpect(status().isNoContent())
                        .andReturn()
                        .getResponse();
        Cookie renewed = response.getCookie(TestLogin.SESSION_COOKIE);
        assertThat(renewed).isNotNull();
        assertThat(TestLogin.sessionId(renewed)).isNotEqualTo(TestLogin.sessionId(a));
        Cookie aNow = new Cookie(TestLogin.SESSION_COOKIE, renewed.getValue());

        mockMvc.perform(get("/api/me").cookie(aNow)).andExpect(status().isOk());
        mockMvc.perform(get("/api/me").cookie(b)).andExpect(status().isUnauthorized());

        SignupRequests.awaitMailCount(mailSender, "chg@example.com", 1);
        assertThat(mailSender.lastTextFor("chg@example.com").orElseThrow())
                .contains("비밀번호가 변경됐어요")
                .contains("본인이 아니라면")
                .contains("/forgot-password");
    }

    @Test
    @DisplayName("#5 소셜 계정 → 400 PASSWORD_NOT_SUPPORTED")
    void socialAccount() throws Exception {
        long id = members().member().provider("GOOGLE").email("g@gmail.com").create();
        change(TestLogin.loginAs(mockMvc, id), "x", NEW_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_NOT_SUPPORTED"));
    }

    @Test
    @DisplayName(
            "현재와 같은 새 비밀번호 → 400 PASSWORD_SAME_AS_CURRENT, 틀린 현재 비밀번호 → 400 CURRENT_PASSWORD_MISMATCH")
    void sameAndMismatch() throws Exception {
        long id = members().member().email("same@example.com").create();
        Cookie session = TestLogin.loginAs(mockMvc, id);
        change(session, CURRENT, CURRENT, CURRENT)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_SAME_AS_CURRENT"));
        change(session, "Wrong#2026x", NEW_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_MISMATCH"));
        change(session, CURRENT, "short", "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("newPassword"));
    }

    @Test
    @DisplayName("#6 5회 연속 틀린 뒤 6번째는 맞아도 429 + Retry-After(15분)")
    void lockAfterFiveFailures() throws Exception {
        long id = members().member().email("lock@example.com").create();
        Cookie session = TestLogin.loginAs(mockMvc, id);
        for (int i = 0; i < 5; i++) {
            change(session, "Wrong#2026x", NEW_PASSWORD, NEW_PASSWORD)
                    .andExpect(status().isBadRequest());
        }
        change(session, CURRENT, NEW_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_TEMPORARILY_LOCKED"))
                .andExpect(
                        header().string(
                                        "Retry-After",
                                        org.hamcrest.Matchers.matchesPattern("\\d+")))
                .andExpect(
                        result ->
                                assertThat(
                                                Long.parseLong(
                                                        result.getResponse()
                                                                .getHeader("Retry-After")))
                                        .isBetween(14 * 60L, 15 * 60L));
    }

    @Test
    @DisplayName("인증 전 회원도 변경할 수 있고, 정지 세션은 403 ACCOUNT_SUSPENDED")
    void unverifiedAllowedSuspendedRejected() throws Exception {
        long unverified = members().member().email("unv@example.com").emailVerified(false).create();
        change(TestLogin.loginAs(mockMvc, unverified), CURRENT, NEW_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isNoContent());

        long suspended = members().member().email("sus2@example.com").create();
        Cookie session = TestLogin.loginAs(mockMvc, suspended);
        members().suspend(suspended, Instant.now().plus(1, ChronoUnit.DAYS), "스팸");
        change(session, CURRENT, NEW_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));
    }

    @Test
    @DisplayName("비로그인 → 401")
    void anonymous() throws Exception {
        change(null, CURRENT, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isUnauthorized());
    }
}
