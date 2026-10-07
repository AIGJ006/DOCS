package com.team.blog.account.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.MemberFixtures;
import com.team.blog.support.TestLogin;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** 이메일 로그인·로그아웃 (US1 #6, FR-034·036·039·041·057, R-03·R-04). */
class EmailLoginLogoutIntegrationTest extends IntegrationTestBase {

    private static final String EMAIL = "kim755030@naver.com";

    @Autowired private FindByIndexNameSessionRepository<? extends Session> sessions;

    private long memberId;

    @BeforeEach
    void createMember() {
        memberId = members().member().handle("kim755030").nickname("김민서").email(EMAIL).create();
        agreeCurrent(memberId);
    }

    @Test
    void loginReturnsJsonResultAndCreatesSession() throws Exception {
        MvcResult result =
                login(" Kim755030@Naver.COM ", MemberFixtures.DEFAULT_PASSWORD, null)
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.redirectTo").value("/"))
                        .andExpect(jsonPath("$.reagreementRequired").value(false))
                        .andExpect(jsonPath("$.accountStatus").value("ACTIVE"))
                        .andReturn();
        Cookie session = result.getResponse().getCookie("SESSION");
        assertThat(session).isNotNull();

        mockMvc.perform(get("/api/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.handle").value("kim755030"))
                .andExpect(jsonPath("$.emailVerified").value(true));
    }

    @Test
    void loginIssuesNewSessionIdEachTime() throws Exception {
        Cookie first = sessionOf(login(EMAIL, MemberFixtures.DEFAULT_PASSWORD, null));
        Cookie second = sessionOf(login(EMAIL, MemberFixtures.DEFAULT_PASSWORD, first));
        assertThat(TestLogin.sessionId(second)).isNotEqualTo(TestLogin.sessionId(first));
        mockMvc.perform(get("/api/me").cookie(first)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/me").cookie(second)).andExpect(status().isOk());
    }

    @Test
    void loginRecordsPreviousLoginAndProviderInSession() throws Exception {
        Cookie firstSession = sessionOf(login(EMAIL, MemberFixtures.DEFAULT_PASSWORD, null));
        Session first = sessions.findById(TestLogin.sessionId(firstSession));
        assertThat((Object) first.getAttribute("previousLoginAt")).isNull(); // 첫 로그인
        assertThat((Object) first.getAttribute("provider")).isEqualTo("LOCAL");
        Instant recorded =
                jdbc.queryForObject(
                                "SELECT last_login_at FROM auth_identity WHERE member_id = ?",
                                Timestamp.class,
                                memberId)
                        .toInstant();
        assertThat(recorded)
                .isCloseTo(
                        Instant.now(),
                        org.assertj.core.api.Assertions.within(1, ChronoUnit.MINUTES));

        Instant earlier = Instant.parse("2026-10-01T03:00:00Z");
        jdbc.update(
                "UPDATE auth_identity SET last_login_at = ? WHERE member_id = ?",
                Timestamp.from(earlier),
                memberId);
        Cookie secondSession = sessionOf(login(EMAIL, MemberFixtures.DEFAULT_PASSWORD, null));
        Session second = sessions.findById(TestLogin.sessionId(secondSession));
        assertThat((Instant) second.getAttribute("previousLoginAt")).isEqualTo(earlier);
        assertThat(
                        jdbc.queryForObject(
                                        "SELECT last_login_at FROM auth_identity WHERE member_id = ?",
                                        Timestamp.class,
                                        memberId)
                                .toInstant())
                .isAfter(earlier);
    }

    @Test
    void sessionLastsFourteenDays() throws Exception {
        Cookie session = sessionOf(login(EMAIL, MemberFixtures.DEFAULT_PASSWORD, null));
        Session stored = sessions.findById(TestLogin.sessionId(session));
        assertThat(stored.getMaxInactiveInterval()).isEqualTo(Duration.ofDays(14));
    }

    @Test
    void wrongPasswordAndUnknownEmailGetTheSameAnswer() throws Exception {
        String wrongPassword =
                login(EMAIL, "Wrong#2026a", null)
                        .andExpect(status().isUnauthorized())
                        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                        .andExpect(jsonPath("$.message").value("이메일 또는 비밀번호가 올바르지 않아요"))
                        .andExpect(jsonPath("$.errors").isArray())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String unknownEmail =
                login("nobody@naver.com", MemberFixtures.DEFAULT_PASSWORD, null)
                        .andExpect(status().isUnauthorized())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(unknownEmail).isEqualTo(wrongPassword);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT last_login_at FROM auth_identity WHERE member_id = ?",
                                Object.class,
                                memberId))
                .isNull();
    }

    @Test
    void socialAccountCannotLoginWithPassword() throws Exception {
        members().member().provider("GOOGLE").email("social@gmail.com").create();
        login("social@gmail.com", MemberFixtures.DEFAULT_PASSWORD, null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void loginRequiresCsrfToken() throws Exception {
        mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("email", EMAIL)
                                .param("password", MemberFixtures.DEFAULT_PASSWORD))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_REJECTED"));
    }

    @Test
    void logoutDeletesServerSessionAndIsIdempotent() throws Exception {
        Cookie session = sessionOf(login(EMAIL, MemberFixtures.DEFAULT_PASSWORD, null));
        String sessionId = TestLogin.sessionId(session);

        mockMvc.perform(TestLogin.withCsrf(post("/api/auth/logout"), session))
                .andExpect(status().isNoContent());
        assertThat(sessions.findById(sessionId)).isNull();
        mockMvc.perform(get("/api/me").cookie(session))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_REQUIRED"));

        mockMvc.perform(TestLogin.withCsrf(post("/api/auth/logout"), session))
                .andExpect(status().isNoContent());
        mockMvc.perform(TestLogin.withCsrf(post("/api/auth/logout"), null))
                .andExpect(status().isNoContent());
    }

    @Test
    void withdrawnMemberCanLoginAndSeesWithdrawnStatus() throws Exception {
        long withdrawn = members().member().email("gone@naver.com").status("WITHDRAWN").create();
        assertThat(withdrawn).isPositive();
        login("gone@naver.com", MemberFixtures.DEFAULT_PASSWORD, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountStatus").value("WITHDRAWN"));
    }

    /** 현재 버전 약관·처리방침 동의 (픽스처는 동의 행을 만들지 않는다 — 없으면 재동의 대상). */
    private void agreeCurrent(long id) {
        for (String type : new String[] {"TERMS", "PRIVACY"}) {
            jdbc.update(
                    "INSERT INTO member_agreement (member_id, type, version) VALUES (?, ?, ?)",
                    id,
                    type,
                    SignupRequests.CURRENT_VERSION);
        }
    }

    @Test
    void outdatedAgreementIsReportedAtLogin() throws Exception {
        jdbc.update(
                "UPDATE member_agreement SET version = '2025-01-01' WHERE member_id = ? AND type = 'TERMS'",
                memberId);
        login(EMAIL, MemberFixtures.DEFAULT_PASSWORD, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reagreementRequired").value(true));
    }

    private ResultActions login(String email, String password, Cookie session) throws Exception {
        return mockMvc.perform(
                TestLogin.withCsrf(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("email", email)
                                .param("password", password),
                        session));
    }

    private static Cookie sessionOf(ResultActions actions) throws Exception {
        Cookie cookie =
                actions.andExpect(status().isOk()).andReturn().getResponse().getCookie("SESSION");
        assertThat(cookie).isNotNull();
        return new Cookie("SESSION", cookie.getValue());
    }
}
