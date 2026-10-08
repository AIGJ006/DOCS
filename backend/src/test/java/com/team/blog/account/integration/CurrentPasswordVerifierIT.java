package com.team.blog.account.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.account.application.CurrentPasswordVerifier;
import com.team.blog.account.domain.AuthIdentity;
import com.team.blog.account.infra.AuthIdentityRepository;
import com.team.blog.shared.error.ApiException;
import com.team.blog.shared.error.TooManyRequestsException;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.MemberFixtures;
import com.team.blog.support.RedisOutage;
import com.team.blog.support.TestLogin;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * 현재 비밀번호 확인 공용화 (015 T008, research R3, Clarifications Q3). 비밀번호 변경(001)과 탈퇴(015)가 같은 실패 기록 {@code
 * auth:pw-change-fail:{memberId}}을 쓴다. 탈퇴 API로 합산하는 경우는 {@code WithdrawalApiIT}도 확인한다.
 */
class CurrentPasswordVerifierIT extends IntegrationTestBase {

    private static final String RIGHT = MemberFixtures.DEFAULT_PASSWORD;
    private static final String WRONG = "Wrong#2026x";

    @Autowired CurrentPasswordVerifier verifier;
    @Autowired AuthIdentityRepository authIdentities;

    private AuthIdentity identity(long memberId) {
        return authIdentities.findByMemberId(memberId).orElseThrow();
    }

    private String code(Runnable call) {
        try {
            call.run();
            return null;
        } catch (ApiException e) {
            return e.reasonCode().code();
        }
    }

    private void changePassword(Cookie session, String current, int expectedStatus)
            throws Exception {
        mockMvc.perform(
                        TestLogin.withCsrf(
                                post("/api/me/password")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"currentPassword\":\""
                                                        + current
                                                        + "\",\"newPassword\":\"Fresh#2026b\","
                                                        + "\"newPasswordConfirm\":\"Fresh#2026b\"}"),
                                session))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void 틀린_비밀번호_5번_뒤에는_맞아도_429_Retry_After() throws Exception {
        long id = members().member().create();
        AuthIdentity identity = identity(id);
        for (int i = 0; i < 5; i++) {
            assertThat(code(() -> verifier.verify(identity, WRONG, "password")))
                    .isEqualTo("CURRENT_PASSWORD_MISMATCH");
        }
        assertThatThrownBy(() -> verifier.verify(identity, RIGHT, "password"))
                .isInstanceOfSatisfying(
                        TooManyRequestsException.class,
                        e -> {
                            assertThat(e.reasonCode().code())
                                    .isEqualTo("PASSWORD_CHANGE_TEMPORARILY_LOCKED");
                            assertThat(e.retryAfterSeconds()).isBetween(14 * 60L, 15 * 60L);
                        });

        // 같은 잠금이 비밀번호 변경 화면에도 걸린다 (HTTP 429 + Retry-After)
        mockMvc.perform(
                        TestLogin.withCsrf(
                                post("/api/me/password")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"currentPassword\":\""
                                                        + RIGHT
                                                        + "\",\"newPassword\":\"Fresh#2026b\","
                                                        + "\"newPasswordConfirm\":\"Fresh#2026b\"}"),
                                TestLogin.loginAs(mockMvc, id)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_TEMPORARILY_LOCKED"))
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void 비밀번호_변경_3번과_탈퇴_확인_2번으로도_잠긴다() throws Exception {
        long id = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, id);
        for (int i = 0; i < 3; i++) {
            changePassword(session, WRONG, 400);
        }
        AuthIdentity identity = identity(id);
        for (int i = 0; i < 2; i++) {
            assertThat(code(() -> verifier.verify(identity, WRONG, "password")))
                    .isEqualTo("CURRENT_PASSWORD_MISMATCH");
        }
        assertThat(code(() -> verifier.verify(identity, RIGHT, "password")))
                .isEqualTo("PASSWORD_CHANGE_TEMPORARILY_LOCKED");
        changePassword(session, RIGHT, 429);
    }

    @Test
    void 맞으면_실패_기록을_지운다() {
        long id = members().member().create();
        AuthIdentity identity = identity(id);
        for (int i = 0; i < 4; i++) {
            code(() -> verifier.verify(identity, WRONG, "password"));
        }
        assertThat(redis.opsForValue().get("auth:pw-change-fail:" + id)).isEqualTo("4");
        verifier.verify(identity, RIGHT, "password");
        assertThat(redis.hasKey("auth:pw-change-fail:" + id)).isFalse();
        assertThat(code(() -> verifier.verify(identity, WRONG, "password")))
                .isEqualTo("CURRENT_PASSWORD_MISMATCH");
        assertThat(redis.opsForValue().get("auth:pw-change-fail:" + id)).isEqualTo("1");
    }

    @Test
    void 빈_비밀번호는_거부하되_세지_않는다() {
        long id = members().member().create();
        AuthIdentity identity = identity(id);
        for (int i = 0; i < 6; i++) {
            assertThat(code(() -> verifier.verify(identity, "", "password")))
                    .isEqualTo("CURRENT_PASSWORD_MISMATCH");
            assertThat(code(() -> verifier.verify(identity, null, "password")))
                    .isEqualTo("CURRENT_PASSWORD_MISMATCH");
        }
        assertThat(redis.hasKey("auth:pw-change-fail:" + id)).isFalse();
        verifier.verify(identity, RIGHT, "password");
    }

    @Test
    void 칸_오류_이름은_부르는_쪽이_정한다() {
        long id = members().member().create();
        assertThatThrownBy(() -> verifier.verify(identity(id), WRONG, "password"))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        e ->
                                assertThat(e.errors())
                                        .extracting("field")
                                        .containsExactly("password"));
    }

    @Test
    void Redis_정지_중에는_비교만_하고_통과() {
        long id = members().member().create();
        AuthIdentity identity = identity(id);
        try (RedisOutage outage = RedisOutage.start()) {
            verifier.verify(identity, RIGHT, "password");
            assertThat(code(() -> verifier.verify(identity, WRONG, "password")))
                    .isEqualTo("CURRENT_PASSWORD_MISMATCH");
        }
    }
}
