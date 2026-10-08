package com.team.blog.account.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.team.blog.interaction.support.CommentFixtures;
import com.team.blog.shared.event.MemberRestored;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.MemberFixtures;
import com.team.blog.support.TestLogin;
import com.team.blog.support.fixture.PostFixtures;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.ResultActions;

/** US2 유예 중 복구 (015 T032, FR-017~022, FR-021a). */
@RecordApplicationEvents
class RestoreApiIT extends IntegrationTestBase {

    private static final String PASSWORD = MemberFixtures.DEFAULT_PASSWORD;

    @Autowired ApplicationEvents events;

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(
                TestLogin.withCsrf(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("email", email)
                                .param("password", password),
                        null));
    }

    private static Cookie sessionOf(ResultActions actions) {
        Cookie cookie = actions.andReturn().getResponse().getCookie("SESSION");
        assertThat(cookie).isNotNull();
        return new Cookie("SESSION", cookie.getValue());
    }

    private ResultActions restore(Cookie session) throws Exception {
        return mockMvc.perform(TestLogin.withCsrf(post("/api/me/restore"), session));
    }

    private void withdraw(Cookie session) throws Exception {
        mockMvc.perform(
                        TestLogin.withCsrf(
                                post("/api/me/withdraw")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"confirmed\":true,\"password\":\""
                                                        + PASSWORD
                                                        + "\"}"),
                                session))
                .andExpect(status().isOk());
    }

    private void withdrawnAt(long id, Instant at) {
        jdbc.update(
                "UPDATE member SET status = 'WITHDRAWN', withdrawn_at = ? WHERE id = ?",
                Timestamp.from(at),
                id);
    }

    private String memberStatus(long id) {
        return jdbc.queryForObject("SELECT status FROM member WHERE id = ?", String.class, id);
    }

    private String body(String url, Cookie viewer) throws Exception {
        var request = get(url);
        if (viewer != null) {
            request.cookie(viewer);
        }
        return mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    @Test
    void US2_1_로그인하면_복구_상태() throws Exception {
        long me = members().member().email("back@example.com").create();
        SignupRequests.agreeCurrent(jdbc, me);
        withdraw(TestLogin.loginAs(mockMvc, me));

        ResultActions login =
                login("back@example.com", PASSWORD)
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.accountStatus").value("WITHDRAWN"));
        Cookie session = sessionOf(login);
        Timestamp withdrawnAt =
                jdbc.queryForObject(
                        "SELECT withdrawn_at FROM member WHERE id = ?", Timestamp.class, me);
        mockMvc.perform(get("/api/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.restoreExpired").value(false))
                .andExpect(
                        jsonPath("$.restoreDeadline")
                                .value(
                                        withdrawnAt
                                                .toInstant()
                                                .plus(Duration.ofDays(30))
                                                .toString()));
    }

    @Test
    void 활동_회원의_me는_기한_null() throws Exception {
        long me = members().member().create();
        mockMvc.perform(get("/api/me").cookie(TestLogin.loginAs(mockMvc, me)))
                .andExpect(jsonPath("$.restoreDeadline").doesNotExist())
                .andExpect(jsonPath("$.restoreExpired").value(false));
    }

    @Test
    void US2_2_다른_요청은_403_허용_목록만_통과() throws Exception {
        long me = members().member().status("WITHDRAWN").create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        mockMvc.perform(get("/api/me").cookie(session)).andExpect(status().isOk());
        mockMvc.perform(get("/api/auth/csrf").cookie(session))
                .andExpect(status().is2xxSuccessful());
        for (String url : List.of("/api/me/settings", "/api/me/profile", "/api/me/posts")) {
            mockMvc.perform(get(url).cookie(session))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCOUNT_WITHDRAWN"));
        }
        mockMvc.perform(TestLogin.withCsrf(post("/api/posts"), session))
                .andExpect(status().isForbidden());
        restore(session).andExpect(status().isOk());
    }

    @Test
    void US2_3_복구하면_그대로() throws Exception {
        long me = members().member().handle("comeback1").create();
        long viewer = members().member().create();
        PostFixtures posts = new PostFixtures(jdbc);
        long myPost = posts.create(me, PostFixtures.State.PUBLISHED_PUBLIC);
        long othersPost = posts.create(viewer, PostFixtures.State.PUBLISHED_PUBLIC);
        CommentFixtures comments = new CommentFixtures(jdbc);
        comments.on(myPost, viewer).create();
        comments.on(othersPost, me).create();
        jdbc.update("INSERT INTO post_like (post_id, member_id) VALUES (?, ?)", myPost, viewer);
        jdbc.update("UPDATE post SET like_count = 1 WHERE id = ?", myPost);
        Cookie viewerSession = TestLogin.loginAs(mockMvc, viewer);
        List<String> urls =
                List.of(
                        "/api/members/comeback1",
                        "/api/members/comeback1/posts",
                        "/api/posts/" + myPost,
                        "/api/posts/" + myPost + "/comments",
                        "/api/posts/" + othersPost + "/comments");
        List<String> before = new java.util.ArrayList<>();
        for (String url : urls) {
            before.add(body(url, viewerSession));
        }
        List<Long> homeBefore = homeIds();

        Cookie session = TestLogin.loginAs(mockMvc, me);
        withdraw(session);
        mockMvc.perform(get("/api/posts/" + myPost).cookie(viewerSession))
                .andExpect(status().isNotFound());

        Cookie again = TestLogin.loginAs(mockMvc, me);
        restore(again)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(
                        header().string(
                                        "Cache-Control",
                                        org.hamcrest.Matchers.containsString("no-store")));

        for (int i = 0; i < urls.size(); i++) {
            assertThat(body(urls.get(i), viewerSession)).as(urls.get(i)).isEqualTo(before.get(i));
        }
        assertThat(homeIds()).isEqualTo(homeBefore);
        assertThat(memberStatus(me)).isEqualTo("ACTIVE");
        assertThat(events.stream(MemberRestored.class)).hasSize(1);
        // 복구한 세션은 그대로 쓴다
        mockMvc.perform(get("/api/me/settings").cookie(again)).andExpect(status().isOk());
    }

    private List<Long> homeIds() throws Exception {
        List<Number> ids = JsonPath.read(body("/api/posts", null), "$.items[*].id");
        return ids.stream().map(Number::longValue).toList();
    }

    @Test
    void US2_4_로그아웃만_하면_유예_계속() throws Exception {
        long me = members().member().status("WITHDRAWN").create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        mockMvc.perform(TestLogin.withCsrf(post("/api/auth/logout"), session))
                .andExpect(status().is2xxSuccessful());
        assertThat(memberStatus(me)).isEqualTo("WITHDRAWN");
        assertThat(events.stream(MemberRestored.class)).isEmpty();
    }

    @Test
    void US2_5_비밀번호_재설정_허용() throws Exception {
        long me = members().member().email("reset@example.com").status("WITHDRAWN").create();
        SignupRequests.agreeCurrent(jdbc, me);
        mockMvc.perform(
                        TestLogin.withCsrf(
                                post("/api/auth/password-reset")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"email\":\"reset@example.com\"}"),
                                null))
                .andExpect(status().is2xxSuccessful());
        SignupRequests.awaitMailCount(mailSender, "reset@example.com", 1);
        String token = mailSender.lastTokenFor("reset@example.com").orElseThrow();
        mockMvc.perform(
                        TestLogin.withCsrf(
                                post("/api/auth/password-reset/confirm")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"token\":\""
                                                        + token
                                                        + "\",\"newPassword\":\"Fresh#2026b\","
                                                        + "\"newPasswordConfirm\":\"Fresh#2026b\"}"),
                                null))
                .andExpect(status().is2xxSuccessful());
        login("reset@example.com", "Fresh#2026b")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountStatus").value("WITHDRAWN"));
        assertThat(memberStatus(me)).isEqualTo("WITHDRAWN");
    }

    @Test
    void US2_6_같은_이메일_가입_안내() throws Exception {
        members().member().email("pending@example.com").status("WITHDRAWN").create();
        mockMvc.perform(SignupRequests.body("pending@example.com", "newhandle1", "새닉네임").request())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(
                        jsonPath("$.errors[?(@.field == 'email')].code")
                                .value("EMAIL_WITHDRAWAL_PENDING"))
                .andExpect(
                        jsonPath("$.errors[?(@.field == 'email')].message")
                                .value("탈퇴 신청한 계정이 있어요. 로그인하면 복구할 수 있어요"));
        // 활동 중인 계정은 그대로 EMAIL_ALREADY_REGISTERED
        members().member().email("taken@example.com").create();
        mockMvc.perform(SignupRequests.body("taken@example.com", "newhandle2", "새닉네임둘").request())
                .andExpect(
                        jsonPath("$.errors[?(@.field == 'email')].code")
                                .value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void 기한_직전은_200() throws Exception {
        long me = members().member().create();
        withdrawnAt(me, Instant.now().minus(Duration.ofDays(30)).plusSeconds(5));
        restore(TestLogin.loginAs(mockMvc, me)).andExpect(status().isOk());
        assertThat(memberStatus(me)).isEqualTo("ACTIVE");
    }

    @Test
    void 기한이_지나면_409이고_me는_restoreExpired() throws Exception {
        long me = members().member().create();
        withdrawnAt(me, Instant.now().minus(Duration.ofDays(30)).minusSeconds(1));
        Cookie session = TestLogin.loginAs(mockMvc, me);
        restore(session)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESTORE_PERIOD_EXPIRED"))
                .andExpect(jsonPath("$.message").value("복구 기한이 지났어요"));
        mockMvc.perform(get("/api/me").cookie(session))
                .andExpect(jsonPath("$.restoreExpired").value(true));
        assertThat(memberStatus(me)).isEqualTo("WITHDRAWN");
        assertThat(events.stream(MemberRestored.class)).isEmpty();
    }

    @Test
    void 활동_회원의_복구는_200_변화_없음() throws Exception {
        long me = members().member().create();
        restore(TestLogin.loginAs(mockMvc, me))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        assertThat(events.stream(MemberRestored.class)).isEmpty();
    }

    @Test
    void 익명_처리된_회원의_세션은_401() throws Exception {
        long me = members().member().status("WITHDRAWN").create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        jdbc.update("UPDATE member SET deleted_at = now(), nickname = NULL WHERE id = ?", me);
        restore(session).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/me").cookie(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void 비로그인은_401() throws Exception {
        restore(null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_REQUIRED"));
    }
}
