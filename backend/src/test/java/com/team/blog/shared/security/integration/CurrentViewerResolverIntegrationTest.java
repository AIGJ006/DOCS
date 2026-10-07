package com.team.blog.shared.security.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.RedisOutage;
import com.team.blog.support.SqlCounter;
import com.team.blog.support.TestLogin;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;

/**
 * {@code Viewer}는 세션의 회원 번호로만 만든다 (004 T011, FR-026, research R-07·R-23). 같은 요청에서 001 탈퇴 유예 필터가 읽은
 * 회원 정보를 다시 조회하지 않는다.
 */
class CurrentViewerResolverIntegrationTest extends IntegrationTestBase {

    @Test
    void 비회원은_anonymous() throws Exception {
        mockMvc.perform(get("/api/__test/viewer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.id").doesNotExist());
    }

    @Test
    void 로그인_회원의_역할_상태_인증_여부를_PK_조회_한_번으로_채운다() throws Exception {
        long id = members().member().emailVerified(false).create();
        Cookie session = TestLogin.loginAs(mockMvc, id);

        try (SqlCounter.Scope scope = SqlCounter.start()) {
            mockMvc.perform(get("/api/__test/viewer").cookie(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.authenticated").value(true))
                    .andExpect(jsonPath("$.id").value(id))
                    .andExpect(jsonPath("$.role").value("USER"))
                    .andExpect(jsonPath("$.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.emailVerified").value(false));
            assertThat(scope.count()).as("탈퇴 유예 필터의 조회를 재사용").isEqualTo(1);
        }
    }

    @Test
    void 요청의_memberId_authorId는_무시한다() throws Exception {
        long me = members().member().create();
        long other = members().member().role("ADMIN").create();
        Cookie session = TestLogin.loginAs(mockMvc, me);

        mockMvc.perform(
                        get("/api/__test/viewer")
                                .param("memberId", String.valueOf(other))
                                .param("authorId", String.valueOf(other))
                                .param("id", String.valueOf(other))
                                .cookie(session))
                .andExpect(jsonPath("$.id").value(me))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void DB의_상태_변경이_다음_요청에_바로_반영된다() throws Exception {
        long id = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, id);
        jdbc.update("UPDATE member SET role = 'ADMIN' WHERE id = ?", id);

        mockMvc.perform(get("/api/__test/viewer").cookie(session))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void 익명_처리된_회원의_세션은_anonymous() throws Exception {
        long id = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, id);
        jdbc.update(
                "UPDATE member SET status = 'WITHDRAWN', withdrawn_at = now(), deleted_at = now()"
                        + " WHERE id = ?",
                id);

        mockMvc.perform(get("/api/__test/viewer").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false));
    }

    @Test
    void 세션_저장소_장애면_anonymous() throws Exception {
        long id = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, id);

        try (RedisOutage outage = RedisOutage.start()) {
            mockMvc.perform(get("/api/__test/viewer").cookie(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.authenticated").value(false));
        }
    }
}
