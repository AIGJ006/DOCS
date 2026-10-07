package com.team.blog.shared.security.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.RedisOutage;
import com.team.blog.support.TestLogin;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

/** Redis 장애 시 세션을 읽지 못하면 비로그인으로 처리하고 읽기는 계속한다 (FR-040, 02 §2-1, H8). */
class SessionResilienceIntegrationTest extends IntegrationTestBase {

    @Test
    void Redis가_멈추면_비로그인으로_처리하고_복구되면_같은_쿠키로_다시_로그인_상태() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        mockMvc.perform(get("/api/me/__probe").cookie(session)).andExpect(status().isOk());

        try (RedisOutage outage = RedisOutage.start()) {
            long started = System.nanoTime();
            MockHttpServletResponse publicResponse =
                    mockMvc.perform(get("/api/__public-probe").cookie(session))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.loggedIn").value(false))
                            .andReturn()
                            .getResponse();
            long publicMillis = (System.nanoTime() - started) / 1_000_000;

            started = System.nanoTime();
            MockHttpServletResponse meResponse =
                    mockMvc.perform(get("/api/me/__probe").cookie(session))
                            .andExpect(status().isUnauthorized())
                            .andExpect(jsonPath("$.code").value("LOGIN_REQUIRED"))
                            .andReturn()
                            .getResponse();
            long meMillis = (System.nanoTime() - started) / 1_000_000;

            // spring.data.redis.timeout(500ms) + α
            assertThat(publicMillis).isLessThan(1_500);
            assertThat(meMillis).isLessThan(1_500);
            // 장애 동안 브라우저의 세션 쿠키를 지우지 않는다
            assertThat(publicResponse.getHeaders("Set-Cookie"))
                    .noneMatch(h -> h.startsWith("SESSION="));
            assertThat(meResponse.getHeaders("Set-Cookie"))
                    .noneMatch(h -> h.startsWith("SESSION="));
        }

        mockMvc.perform(get("/api/me/__probe").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(me));
    }
}
