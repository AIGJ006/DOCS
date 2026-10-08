package com.team.blog.account.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** US6 계정 설정 (FR-046·053·061). */
class AccountSettingsIntegrationTest extends IntegrationTestBase {

    private ResultActions save(Cookie session, String json) throws Exception {
        return mockMvc.perform(
                TestLogin.withCsrf(
                        patch("/api/me/settings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json),
                        session));
    }

    private String column(String column, long id) {
        return jdbc.queryForObject(
                "SELECT " + column + "::text FROM member WHERE id = ?", String.class, id);
    }

    @Test
    @DisplayName("GET /api/me/settings → 이메일·로그인 수단·기본 공개 범위·최근 활동 공개·비밀번호 변경 가능")
    void readSettings() throws Exception {
        long id = members().member().email("set@example.com").create();
        mockMvc.perform(get("/api/me/settings").cookie(TestLogin.loginAs(mockMvc, id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("set@example.com"))
                .andExpect(jsonPath("$.provider").value("LOCAL"))
                .andExpect(jsonPath("$.defaultVisibility").value("PUBLIC"))
                .andExpect(jsonPath("$.lastActiveVisible").value(true))
                .andExpect(jsonPath("$.passwordChangeAvailable").value(true))
                .andExpect(jsonPath("$.previousLogin").hasJsonPath());
    }

    @Test
    @DisplayName("소셜 계정은 passwordChangeAvailable false")
    void socialHasNoPassword() throws Exception {
        long id = members().member().provider("GITHUB").email("gh@example.com").create();
        mockMvc.perform(get("/api/me/settings").cookie(TestLogin.loginAs(mockMvc, id)))
                .andExpect(jsonPath("$.provider").value("GITHUB"))
                .andExpect(jsonPath("$.passwordChangeAvailable").value(false));
    }

    @Test
    @DisplayName("기본 공개 범위 PRIVATE → 200, 보낸 칸만 바뀐다")
    void changeDefaultVisibility() throws Exception {
        long id = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, id);
        save(session, "{\"defaultVisibility\":\"PRIVATE\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultVisibility").value("PRIVATE"))
                .andExpect(jsonPath("$.lastActiveVisible").value(true));
        assertThat(column("default_visibility", id)).isEqualTo("PRIVATE");
        assertThat(column("last_active_visible", id)).isEqualTo("true");
    }

    @Test
    @DisplayName("등록되지 않은 공개 범위(FRIENDS)·잘못된 값 → 400 INVALID_VISIBILITY")
    void invalidVisibility() throws Exception {
        long id = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, id);
        for (String value : new String[] {"FRIENDS", "public", "NOPE"}) {
            save(session, "{\"defaultVisibility\":\"" + value + "\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("defaultVisibility"))
                    .andExpect(jsonPath("$.errors[0].code").value("INVALID_VISIBILITY"));
        }
        assertThat(column("default_visibility", id)).isEqualTo("PUBLIC");
    }

    @Test
    @DisplayName("최근 활동 공개 끄기 → 200, 공개 범위는 그대로")
    void changeLastActiveVisible() throws Exception {
        long id = members().member().defaultVisibility("PRIVATE").create();
        save(TestLogin.loginAs(mockMvc, id), "{\"lastActiveVisible\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastActiveVisible").value(false))
                .andExpect(jsonPath("$.defaultVisibility").value("PRIVATE"));
        assertThat(column("last_active_visible", id)).isEqualTo("false");
    }

    @Test
    @DisplayName("빈 본문 → 400")
    void emptyBody() throws Exception {
        long id = members().member().create();
        save(TestLogin.loginAs(mockMvc, id), "{}").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("인증 전 회원도 바꿀 수 있다, 비로그인 401")
    void unverifiedAllowedAnonymousRejected() throws Exception {
        long id = members().member().emailVerified(false).create();
        save(TestLogin.loginAs(mockMvc, id), "{\"lastActiveVisible\":false}")
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/me/settings")).andExpect(status().isUnauthorized());
    }
}
