package com.team.blog.account.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.account.infra.redis.ActiveTouchThrottle;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.MemberFixtures;
import com.team.blog.support.RedisOutage;
import com.team.blog.support.TestLogin;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** US8 직전 로그인·최근 활동 갱신 (FR-057~059·040). */
class PreviousLoginAndActivityIntegrationTest extends IntegrationTestBase {

    @Autowired ActiveTouchThrottle throttle;

    private Cookie login(String email) throws Exception {
        String value =
                mockMvc.perform(
                                TestLogin.withCsrf(
                                        post("/api/auth/login")
                                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                                .param("email", email)
                                                .param("password", MemberFixtures.DEFAULT_PASSWORD),
                                        null))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getCookie(TestLogin.SESSION_COOKIE)
                        .getValue();
        return new Cookie(TestLogin.SESSION_COOKIE, value);
    }

    private long member(String email) {
        long id = members().member().email(email).create();
        SignupRequests.agreeCurrent(jdbc, id);
        return id;
    }

    @Test
    @DisplayName("#1 10월 6일 로그인 기록 뒤 다시 로그인 → previousLogin은 10월 6일 값(방금 로그인 아님)")
    void previousLoginIsTheOneBefore() throws Exception {
        long id = member("prev@example.com");
        Instant oct6 = Instant.parse("2026-10-06T01:30:00Z");
        jdbc.update(
                "UPDATE auth_identity SET last_login_at = ? WHERE member_id = ?",
                Timestamp.from(oct6),
                id);
        Cookie session = login("prev@example.com");
        mockMvc.perform(get("/api/me/settings").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previousLogin.at").value("2026-10-06T01:30:00Z"))
                .andExpect(jsonPath("$.previousLogin.provider").value("LOCAL"));
        Timestamp now =
                jdbc.queryForObject(
                        "SELECT last_login_at FROM auth_identity WHERE member_id = ?",
                        Timestamp.class,
                        id);
        assertThat(now.toInstant()).isAfter(oct6);
    }

    @Test
    @DisplayName("#2 첫 로그인 → previousLogin null(키는 있음)")
    void firstLogin() throws Exception {
        member("first@example.com");
        mockMvc.perform(get("/api/me/settings").cookie(login("first@example.com")))
                .andExpect(jsonPath("$.previousLogin").hasJsonPath())
                .andExpect(jsonPath("$.previousLogin").value((Object) null));
    }

    @Test
    @DisplayName("다른 회원의 직전 로그인을 보는 경로는 없다 (본인 /api/me/settings만)")
    void onlyOwn() throws Exception {
        mockMvc.perform(get("/api/me/settings")).andExpect(status().isUnauthorized());
        long other = member("other@example.com");
        mockMvc.perform(
                        get("/api/members/" + other + "/settings")
                                .cookie(TestLogin.loginAs(mockMvc, member("me@example.com"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("인증된 요청 → last_active_at 갱신, 1시간 안 두 번째 요청은 갱신하지 않는다")
    void touchThrottled() throws Exception {
        long id = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, id);
        redis.delete("member:active-touch:" + id);
        jdbc.update("UPDATE member SET last_active_at = NULL WHERE id = ?", id);
        mockMvc.perform(get("/api/me").cookie(session)).andExpect(status().isOk());
        Timestamp first =
                jdbc.queryForObject(
                        "SELECT last_active_at FROM member WHERE id = ?", Timestamp.class, id);
        assertThat(first).isNotNull();
        assertThat(redis.getExpire("member:active-touch:" + id)).isBetween(3500L, 3600L);
        jdbc.update(
                "UPDATE member SET last_active_at = ? WHERE id = ?",
                Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")),
                id);
        mockMvc.perform(get("/api/me").cookie(session)).andExpect(status().isOk());
        assertThat(
                        jdbc.queryForObject(
                                        "SELECT last_active_at FROM member WHERE id = ?",
                                        Timestamp.class,
                                        id)
                                .toInstant())
                .isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    @DisplayName("비로그인 요청은 갱신하지 않는다")
    void anonymousNotTouched() throws Exception {
        mockMvc.perform(get("/api/agreements/current")).andExpect(status().isOk());
        assertThat(redis.keys("member:active-touch:*")).isEmpty();
    }

    @Test
    @DisplayName("간격 확인: 처음 true, 1시간 안 false, Redis 장애 중에는 false(갱신 건너뜀)이고 예외가 나지 않는다")
    void redisOutageSkips() {
        long id = members().member().create();
        assertThat(throttle.tryAcquire(id)).isTrue();
        assertThat(throttle.tryAcquire(id)).isFalse();
        redis.delete("member:active-touch:" + id);
        try (RedisOutage outage = RedisOutage.start()) {
            assertThat(throttle.tryAcquire(id)).isFalse();
        }
    }
}
