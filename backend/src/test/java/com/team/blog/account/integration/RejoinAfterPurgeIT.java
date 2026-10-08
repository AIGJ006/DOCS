package com.team.blog.account.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.account.application.purge.WithdrawPurgeJob;
import com.team.blog.account.support.WithdrawalPurgeProbe;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 익명 처리 뒤 다시 가입하기 (015 T057, US4, research R13). 001 코드 변경 없이 통과해야 한다: 로그인 수단 행이 지워졌으므로 같은 이메일·소셜
 * 계정이 새 계정을 만들고, 옛 주소는 행이 남아 중복으로, 옛 닉네임은 NULL이 되어 바로 쓸 수 있다.
 */
class RejoinAfterPurgeIT extends IntegrationTestBase {

    @Autowired WithdrawPurgeJob job;
    @Autowired WithdrawalPurgeProbe probe;
    @Autowired FakeSocialProvider provider;

    @BeforeEach
    void setUp() {
        probe.reset();
    }

    private long purged(MemberFixtureSpec spec) {
        long id = spec.create();
        jdbc.update(
                "UPDATE member SET status = 'WITHDRAWN', withdrawn_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minus(Duration.ofDays(31))),
                id);
        assertThat(job.run(Instant.now()).purged()).isEqualTo(1);
        return id;
    }

    @FunctionalInterface
    private interface MemberFixtureSpec {
        long create();
    }

    @Test
    void US4_1_같은_이메일_새_계정() throws Exception {
        long old =
                purged(
                        () ->
                                members()
                                        .member()
                                        .handle("kim755030")
                                        .email("kim@example.com")
                                        .create());

        mockMvc.perform(SignupRequests.body("kim@example.com", "kimnew01", "새김민서").request())
                .andExpect(status().isCreated());

        Long fresh =
                jdbc.queryForObject(
                        "SELECT member_id FROM auth_identity WHERE email = 'kim@example.com'",
                        Long.class);
        assertThat(fresh).isNotEqualTo(old);
        Map<String, Object> oldRow = jdbc.queryForMap("SELECT * FROM member WHERE id = ?", old);
        assertThat(oldRow.get("handle")).isEqualTo("kim755030");
        assertThat(oldRow.get("deleted_at")).isNotNull();
    }

    @Test
    void US4_2_옛_주소는_다른_값_제안() throws Exception {
        purged(() -> members().member().handle("kim755030").email("kim2@example.com").create());

        mockMvc.perform(get("/api/handles/availability").queryParam("handle", "kim755030"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.reason").value("HANDLE_DUPLICATE"))
                .andExpect(jsonPath("$.suggestion").value("kim755030_2"));
        mockMvc.perform(SignupRequests.body("kim2@example.com", "kim755030", "다시온김민서").request())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("handle"))
                .andExpect(jsonPath("$.errors[0].code").value("HANDLE_DUPLICATE"))
                .andExpect(jsonPath("$.details.handleSuggestion").value("kim755030_2"));
    }

    @Test
    void US4_3_옛_닉네임_즉시_사용() throws Exception {
        purged(() -> members().member().nickname("옛닉네임").create());
        long other = members().member().create();
        SignupRequests.agreeCurrent(jdbc, other);

        mockMvc.perform(
                        TestLogin.withCsrf(
                                patch("/api/me/profile")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"nickname\":\"옛닉네임\"}"),
                                TestLogin.loginAs(mockMvc, other)))
                .andExpect(status().is2xxSuccessful());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT nickname FROM member WHERE id = ?", String.class, other))
                .isEqualTo("옛닉네임");
    }

    @Test
    void 같은_소셜_계정으로_다시_로그인하면_새_가입_흐름() throws Exception {
        purged(
                () ->
                        members()
                                .member()
                                .provider("GOOGLE")
                                .providerUserId("google-sub-rejoin")
                                .email("rejoin@gmail.com")
                                .create());
        String code = UUID.randomUUID().toString();
        provider.google(code, "google-sub-rejoin", "rejoin@gmail.com", true, "다시", null);

        MockHttpServletResponse start =
                mockMvc.perform(get("/oauth2/authorization/{id}", "google"))
                        .andReturn()
                        .getResponse();
        String state =
                java.net.URLDecoder.decode(
                        UriComponentsBuilder.fromUriString(start.getRedirectedUrl())
                                .build()
                                .getQueryParams()
                                .getFirst("state"),
                        java.nio.charset.StandardCharsets.UTF_8);
        Cookie session = start.getCookie(TestLogin.SESSION_COOKIE);
        MockHttpServletResponse callback =
                mockMvc.perform(
                                get("/login/oauth2/code/{id}", "google")
                                        .queryParam("code", code)
                                        .queryParam("state", state)
                                        .cookie(session))
                        .andReturn()
                        .getResponse();

        assertThat(callback.getStatus()).isEqualTo(302);
        assertThat(callback.getRedirectedUrl()).isEqualTo("/signup/social");
    }
}
