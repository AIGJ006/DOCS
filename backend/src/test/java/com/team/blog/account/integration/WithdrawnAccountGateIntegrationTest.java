package com.team.blog.account.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 탈퇴 유예 회원은 허용 목록 밖 모든 /api/** 요청이 403 ACCOUNT_WITHDRAWN (004 FR-031, R-23, data-model §4-1). */
class WithdrawnAccountGateIntegrationTest extends IntegrationTestBase {

    private static final String WITHDRAWN_BODY =
            """
            {"code":"ACCOUNT_WITHDRAWN","message":"탈퇴 신청한 계정이에요","errors":[],
             "details":{"action":"RESTORE"}}
            """;

    private MockHttpServletResponse perform(RequestBuilder request) throws Exception {
        return mockMvc.perform(request).andReturn().getResponse();
    }

    private MockHttpServletRequestBuilder mutate(
            MockHttpServletRequestBuilder request, Cookie session) {
        return TestLogin.withCsrf(request, session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}");
    }

    @Test
    void 탈퇴_유예_세션은_읽기와_쓰기_모두_403() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        jdbc.update(
                "UPDATE member SET status = 'WITHDRAWN', withdrawn_at = now() WHERE id = ?", me);

        mockMvc.perform(get("/api/posts/{id}", 1).cookie(session))
                .andExpect(status().isForbidden())
                .andExpect(content().json(WITHDRAWN_BODY, JsonCompareMode.STRICT));
        mockMvc.perform(get("/api/me/settings").cookie(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_WITHDRAWN"));
        mockMvc.perform(mutate(post("/api/posts"), session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.details.action").value("RESTORE"));
        mockMvc.perform(get("/api/__public-probe").cookie(session))
                .andExpect(status().isForbidden());
        mockMvc.perform(mutate(patch("/api/me/settings"), session))
                .andExpect(status().isForbidden());
    }

    @Test
    void 허용_목록_4개는_막지_않는다() throws Exception {
        long me = members().member().status("WITHDRAWN").create();
        Cookie session = TestLogin.loginAs(mockMvc, me);

        MockHttpServletResponse[] allowed = {
            perform(mutate(post("/api/me/restore"), session)),
            perform(mutate(post("/api/auth/logout"), session)),
            perform(get("/api/me").cookie(session)),
            perform(get("/api/auth/csrf").cookie(session)),
        };
        for (MockHttpServletResponse response : allowed) {
            // 아직 없는 경로(015·US1)는 404 — 필터가 막지 않았다는 것만 본다
            assertThat(response.getStatus()).isNotEqualTo(403);
            assertThat(response.getContentAsString()).doesNotContain("ACCOUNT_WITHDRAWN");
        }
        assertThat(allowed[3].getStatus()).isEqualTo(204);
    }

    @Test
    void 허용_경로라도_메서드가_다르면_막는다() throws Exception {
        long me = members().member().status("WITHDRAWN").create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        mockMvc.perform(get("/api/me/restore").cookie(session)).andExpect(status().isForbidden());
        mockMvc.perform(mutate(patch("/api/me"), session)).andExpect(status().isForbidden());
    }

    @Test
    void ACTIVE_SUSPENDED_비로그인은_영향_없다() throws Exception {
        long active = members().member().create();
        long suspended = members().member().create();
        members().suspend(suspended, Instant.now().plus(1, ChronoUnit.DAYS), "스팸");

        mockMvc.perform(get("/api/__public-probe").cookie(TestLogin.loginAs(mockMvc, active)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loggedIn").value(true));
        mockMvc.perform(get("/api/__public-probe").cookie(TestLogin.loginAs(mockMvc, suspended)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/__public-probe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loggedIn").value(false));
        mockMvc.perform(get("/api/me/__probe")).andExpect(status().isUnauthorized());
    }

    @Test
    void 화면_경로는_막지_않는다() throws Exception {
        long me = members().member().status("WITHDRAWN").create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        mockMvc.perform(get("/settings").cookie(session)).andExpect(status().isOk());
    }

    @Test
    void DB에서_상태를_바꾸면_다음_요청에_바로_반영() throws Exception {
        long me = members().member().status("WITHDRAWN").create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        mockMvc.perform(get("/api/me/__probe").cookie(session)).andExpect(status().isForbidden());

        jdbc.update("UPDATE member SET status = 'ACTIVE', withdrawn_at = NULL WHERE id = ?", me);
        mockMvc.perform(get("/api/me/__probe").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(me));

        jdbc.update(
                "UPDATE member SET status = 'WITHDRAWN', withdrawn_at = now() WHERE id = ?", me);
        mockMvc.perform(get("/api/me/__probe").cookie(session)).andExpect(status().isForbidden());
    }

    @Test
    void 조회한_회원_정보를_요청_attribute에_둔다() throws Exception {
        long me = members().member().emailVerified(false).create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        mockMvc.perform(get("/api/me/__access-info").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.emailVerified").value(false));
    }
}
