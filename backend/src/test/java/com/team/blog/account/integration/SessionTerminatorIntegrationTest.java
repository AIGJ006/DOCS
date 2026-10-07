package com.team.blog.account.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.account.application.SessionTerminator;
import com.team.blog.shared.error.TemporarilyUnavailableException;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.RedisOutage;
import com.team.blog.support.TestLogin;
import jakarta.servlet.http.Cookie;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 회원의 모든 세션 삭제 (R-03, FR-044·045, 42 P-7). */
class SessionTerminatorIntegrationTest extends IntegrationTestBase {

    @Autowired SessionTerminator sessionTerminator;

    private int statusOf(Cookie session) throws Exception {
        return mockMvc.perform(get("/api/me/__probe").cookie(session))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    @Test
    void 그_회원의_세션만_모두_지운다() throws Exception {
        long me = members().member().create();
        long other = members().member().create();
        Cookie s1 = TestLogin.loginAs(mockMvc, me);
        Cookie s2 = TestLogin.loginAs(mockMvc, me);
        Cookie s3 = TestLogin.loginAs(mockMvc, me);
        Cookie otherSession = TestLogin.loginAs(mockMvc, other);

        int deleted = sessionTerminator.terminateAll(me, Optional.empty());

        assertThat(deleted).isEqualTo(3);
        for (Cookie s : new Cookie[] {s1, s2, s3}) {
            assertThat(statusOf(s)).isEqualTo(401);
        }
        mockMvc.perform(get("/api/me/__probe").cookie(otherSession)).andExpect(status().isOk());
    }

    @Test
    void 현재_세션은_남길_수_있다() throws Exception {
        long me = members().member().create();
        Cookie current = TestLogin.loginAs(mockMvc, me);
        Cookie old1 = TestLogin.loginAs(mockMvc, me);
        Cookie old2 = TestLogin.loginAs(mockMvc, me);

        int deleted = sessionTerminator.terminateAll(me, Optional.of(TestLogin.sessionId(current)));

        assertThat(deleted).isEqualTo(2);
        assertThat(statusOf(current)).isEqualTo(200);
        assertThat(statusOf(old1)).isEqualTo(401);
        assertThat(statusOf(old2)).isEqualTo(401);
    }

    @Test
    void 세션이_없으면_0() {
        long me = members().member().create();
        assertThat(sessionTerminator.terminateAll(me, Optional.empty())).isZero();
    }

    @Test
    void Redis_장애면_TemporarilyUnavailableException() {
        long me = members().member().create();
        TestLogin.loginAs(mockMvc, me);
        try (RedisOutage outage = RedisOutage.start()) {
            assertThatThrownBy(() -> sessionTerminator.terminateAll(me, Optional.empty()))
                    .isInstanceOf(TemporarilyUnavailableException.class);
        }
    }
}
