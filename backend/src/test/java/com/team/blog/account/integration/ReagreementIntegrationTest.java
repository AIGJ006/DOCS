package com.team.blog.account.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.MemberFixtures;
import com.team.blog.support.TestLogin;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * US5 #6 약관 재동의 (FR-012, SC-011, R-24). 설정 버전을 올린 새 컨텍스트 대신 회원이 동의한 버전을 예전 값으로 두어 "현재 버전 ≠ 저장 버전"을
 * 만든다(같은 판정, 통합 테스트 컨텍스트 하나 유지).
 */
class ReagreementIntegrationTest extends IntegrationTestBase {

    private static final String OLD_VERSION = "2025-01-01";
    private static final String EMAIL = "old@example.com";

    private long memberId;
    private Cookie session;

    @BeforeEach
    void loginWithOldAgreement() throws Exception {
        memberId = members().member().email(EMAIL).create();
        for (String type : new String[] {"TERMS", "PRIVACY"}) {
            jdbc.update(
                    "INSERT INTO member_agreement (member_id, type, version, agreed_at) VALUES (?, ?, ?, ?)",
                    memberId,
                    type,
                    OLD_VERSION,
                    Timestamp.from(Instant.now().minus(300, ChronoUnit.DAYS)));
        }
        var response =
                mockMvc.perform(
                                TestLogin.withCsrf(
                                        post("/api/auth/login")
                                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                                .param("email", EMAIL)
                                                .param("password", MemberFixtures.DEFAULT_PASSWORD),
                                        null))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.reagreementRequired").value(true))
                        .andReturn()
                        .getResponse();
        session = new Cookie(TestLogin.SESSION_COOKIE, response.getCookie("SESSION").getValue());
    }

    @Test
    @DisplayName("재동의 전에는 허용 목록 밖 /api 요청이 403 REAGREEMENT_REQUIRED, 허용 목록은 통과")
    void gateBlocksUntilReagreed() throws Exception {
        mockMvc.perform(get("/api/me/settings").cookie(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REAGREEMENT_REQUIRED"));
        mockMvc.perform(
                        TestLogin.withCsrf(
                                patch("/api/me/profile")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"bio\":\"x\"}"),
                                session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REAGREEMENT_REQUIRED"));
        mockMvc.perform(get("/api/posts/1").cookie(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REAGREEMENT_REQUIRED"));

        mockMvc.perform(get("/api/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reagreementRequired").value(true));
        mockMvc.perform(get("/api/agreements/current").cookie(session)).andExpect(status().isOk());
        mockMvc.perform(get("/api/auth/csrf").cookie(session)).andExpect(status().isNoContent());
        // 화면 셸·정적 파일
        mockMvc.perform(get("/reagree").cookie(session)).andExpect(status().isOk());
        mockMvc.perform(get("/terms").cookie(session)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /api/me/agreements(현재 버전) 204 → 버전·agreed_at 갱신, 이후 다른 요청 통과, 같은 버전 재전송도 204")
    void reagreeLiftsGate() throws Exception {
        Timestamp before =
                jdbc.queryForObject(
                        "SELECT agreed_at FROM member_agreement WHERE member_id = ? AND type = 'TERMS'",
                        Timestamp.class,
                        memberId);
        reagree(SignupRequests.CURRENT_VERSION).andExpect(status().isNoContent());
        assertThat(
                        jdbc.queryForList(
                                "SELECT version FROM member_agreement WHERE member_id = ?",
                                String.class,
                                memberId))
                .containsOnly(SignupRequests.CURRENT_VERSION)
                .hasSize(2);
        Timestamp after =
                jdbc.queryForObject(
                        "SELECT agreed_at FROM member_agreement WHERE member_id = ? AND type = 'TERMS'",
                        Timestamp.class,
                        memberId);
        assertThat(after).isAfter(before);

        mockMvc.perform(get("/api/me/settings").cookie(session))
                .andExpect(
                        result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403));
        mockMvc.perform(get("/api/me").cookie(session))
                .andExpect(jsonPath("$.reagreementRequired").value(false));
        reagree(SignupRequests.CURRENT_VERSION).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("다른 버전으로 재동의 → 400 AGREEMENT_VERSION_MISMATCH, 표시는 그대로")
    void wrongVersion() throws Exception {
        reagree(OLD_VERSION)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("AGREEMENT_VERSION_MISMATCH"));
        mockMvc.perform(get("/api/me/settings").cookie(session))
                .andExpect(jsonPath("$.code").value("REAGREEMENT_REQUIRED"));
    }

    @Test
    @DisplayName("로그아웃은 재동의 전에도 된다")
    void logoutAllowed() throws Exception {
        mockMvc.perform(TestLogin.withCsrf(post("/api/auth/logout"), session))
                .andExpect(status().isNoContent());
    }

    private org.springframework.test.web.servlet.ResultActions reagree(String version)
            throws Exception {
        return mockMvc.perform(
                TestLogin.withCsrf(
                        put("/api/me/agreements")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"termsVersion\":\""
                                                + version
                                                + "\",\"privacyVersion\":\""
                                                + version
                                                + "\"}"),
                        session));
    }
}
