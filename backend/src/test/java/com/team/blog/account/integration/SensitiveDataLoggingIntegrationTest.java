package com.team.blog.account.integration;

import static com.team.blog.account.integration.SignupRequests.awaitMailCount;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 민감 정보가 로그·Redis 키에 남지 않는다 (T144, FR-015). 가입 → 인증 확인 → 로그인 실패 → 로그인 → 비밀번호 변경 → 비밀번호 찾기·재설정 전 과정을
 * 로그 수준을 DEBUG로 올린 채 돌리고, 출력에 비밀번호 원문·토큰 값·저장된 해시가 없는지, Redis 키에 평문 이메일이 없는지 본다. 로그 수준은 실행 중에 바꾸고
 * 끝나면 되돌린다(새 컨텍스트 없음).
 */
@ExtendWith(OutputCaptureExtension.class)
class SensitiveDataLoggingIntegrationTest extends IntegrationTestBase {

    private static final String EMAIL = "logsafe@example.com";
    private static final String FIRST = SignupRequests.PASSWORD;
    private static final String WRONG = "Wrong#Guess2026q";
    private static final String CHANGED = "Next#Secret2026b";
    private static final String RESET = "Third#Sec2026c";
    private static final List<String> LOGGERS =
            List.of("com.team.blog", "org.springframework.security", "org.springframework.web");

    @Autowired LoggingSystem loggingSystem;

    @Test
    @DisplayName("가입·인증 확인·로그인 실패·변경·재설정 전 과정의 로그에 비밀번호·토큰·해시가 없고 Redis 키에 평문 이메일이 없다")
    void noSecretsInLogsOrRedisKeys(CapturedOutput output) throws Exception {
        Map<String, LogLevel> previous = new java.util.HashMap<>();
        for (String logger : LOGGERS) {
            var config = loggingSystem.getLoggerConfiguration(logger);
            previous.put(logger, config == null ? null : config.getConfiguredLevel());
            loggingSystem.setLogLevel(logger, LogLevel.DEBUG);
        }
        try {
            mockMvc.perform(SignupRequests.body(EMAIL, "logsafe01", "로그안전").request())
                    .andExpect(status().isCreated());
            awaitMailCount(mailSender, EMAIL, 1);
            String verifyToken = mailSender.lastTokenFor(EMAIL).orElseThrow();
            postJson("/api/auth/email-verification/confirm", "{\"token\":\"" + verifyToken + "\"}")
                    .andExpect(status().is2xxSuccessful());

            login(WRONG).andExpect(status().isUnauthorized());
            Cookie session = sessionOf(login(FIRST).andExpect(status().isOk()));

            mockMvc.perform(
                            TestLogin.withCsrf(
                                    post("/api/me/password")
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(
                                                    "{\"currentPassword\":\""
                                                            + FIRST
                                                            + "\",\"newPassword\":\""
                                                            + CHANGED
                                                            + "\",\"newPasswordConfirm\":\""
                                                            + CHANGED
                                                            + "\"}"),
                                    session))
                    .andExpect(status().isNoContent());

            // 인증 메일·비밀번호 변경 알림 메일 다음의 재설정 메일을 기다린다.
            awaitMailCount(mailSender, EMAIL, 2);
            postJson("/api/auth/password-reset", "{\"email\":\"" + EMAIL + "\"}")
                    .andExpect(status().isAccepted());
            awaitMailCount(mailSender, EMAIL, 3);
            String resetToken = mailSender.lastTokenFor(EMAIL).orElseThrow();
            postJson(
                            "/api/auth/password-reset/confirm",
                            "{\"token\":\""
                                    + resetToken
                                    + "\",\"newPassword\":\""
                                    + RESET
                                    + "\",\"newPasswordConfirm\":\""
                                    + RESET
                                    + "\"}")
                    .andExpect(status().is2xxSuccessful());
            login(RESET).andExpect(status().isOk());

            String hash =
                    jdbc.queryForObject(
                            "SELECT password_hash FROM auth_identity WHERE email = ?",
                            String.class,
                            EMAIL);
            String logs = output.getAll();
            assertThat(logs).as("로그가 실제로 쌓였는지").contains("DEBUG");
            for (String secret :
                    List.of(FIRST, WRONG, CHANGED, RESET, verifyToken, resetToken, hash)) {
                assertThat(logs).doesNotContain(secret);
            }
            assertThat(logs).doesNotContain("password_hash");

            Set<String> keys = redis.keys("*");
            assertThat(keys).isNotEmpty();
            assertThat(keys)
                    .allSatisfy(
                            key -> {
                                assertThat(key).doesNotContainIgnoringCase(EMAIL);
                                assertThat(key).doesNotContain("@");
                            });
        } finally {
            previous.forEach(loggingSystem::setLogLevel);
        }
    }

    private ResultActions login(String password) throws Exception {
        return mockMvc.perform(
                TestLogin.withCsrf(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("email", EMAIL)
                                .param("password", password),
                        null));
    }

    private ResultActions postJson(String path, String body) throws Exception {
        return mockMvc.perform(
                TestLogin.withCsrf(
                        post(path).contentType(MediaType.APPLICATION_JSON).content(body), null));
    }

    private static Cookie sessionOf(ResultActions actions) {
        MockHttpServletResponse response = actions.andReturn().getResponse();
        Cookie cookie = response.getCookie(TestLogin.SESSION_COOKIE);
        assertThat(cookie).isNotNull();
        return new Cookie(TestLogin.SESSION_COOKIE, cookie.getValue());
    }
}
