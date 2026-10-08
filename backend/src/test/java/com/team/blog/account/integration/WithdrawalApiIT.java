package com.team.blog.account.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.account.application.WithdrawCommand;
import com.team.blog.account.application.WithdrawalService;
import com.team.blog.interaction.support.CommentFixtures;
import com.team.blog.shared.error.TemporarilyUnavailableException;
import com.team.blog.shared.event.MemberWithdrawn;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.MemberFixtures;
import com.team.blog.support.RedisOutage;
import com.team.blog.support.TestLogin;
import com.team.blog.support.fixture.PostFixtures;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** US1 탈퇴 안내·신청 (015 T016, quickstart §2 WithdrawalApiIT). */
@RecordApplicationEvents
@ExtendWith(OutputCaptureExtension.class)
class WithdrawalApiIT extends IntegrationTestBase {

    private static final String PASSWORD = MemberFixtures.DEFAULT_PASSWORD;
    private static final String WRONG = "Wrong#2026x";

    @Autowired ApplicationEvents events;
    @Autowired WithdrawalService withdrawalService;

    private PostFixtures posts() {
        return new PostFixtures(jdbc);
    }

    private ResultActions withdraw(Cookie session, String json) throws Exception {
        return mockMvc.perform(
                TestLogin.withCsrf(
                        post("/api/me/withdraw")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json),
                        session));
    }

    private static String byPassword(String password) {
        return "{\"confirmed\":true,\"password\":\"" + password + "\"}";
    }

    private Map<String, Object> member(long id) {
        return jdbc.queryForMap("SELECT status, withdrawn_at FROM member WHERE id = ?", id);
    }

    @Test
    void US1_1_안내_숫자() throws Exception {
        long me = members().member().handle("kim755030").create();
        long other = members().member().create();
        // 내 글 24개: 발행 20(좋아요 합 126) + 임시 2 + 휴지통 1 + 숨김 1
        List<Long> mine = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            mine.add(posts().create(me, PostFixtures.State.PUBLISHED_PUBLIC));
        }
        posts().create(me, PostFixtures.State.DRAFT);
        posts().create(me, PostFixtures.State.DRAFT);
        posts().create(me, PostFixtures.State.TRASHED);
        posts().create(me, PostFixtures.State.HIDDEN);
        jdbc.update("UPDATE post SET like_count = 6 WHERE author_id = ?", me);
        jdbc.update("UPDATE post SET like_count = 6 + 6 WHERE id = ?", mine.get(0));
        // 6 * 24 = 144 → 126 이 되도록 18 줄인다
        jdbc.update(
                "UPDATE post SET like_count = 0 WHERE id IN (?, ?, ?, ?)",
                mine.get(1),
                mine.get(2),
                mine.get(3),
                mine.get(4));
        // 남의 글 댓글 18 + 지운 댓글 2 + 내 글 댓글 3(세지 않음)
        long othersPost = posts().create(other, PostFixtures.State.PUBLISHED_PUBLIC);
        CommentFixtures comments = new CommentFixtures(jdbc);
        for (int i = 0; i < 18; i++) {
            comments.on(othersPost, me).create();
        }
        comments.on(othersPost, me).deleted().create();
        comments.on(othersPost, me).deleted().create();
        for (int i = 0; i < 3; i++) {
            comments.on(mine.get(5), me).create();
        }
        long likes =
                jdbc.queryForObject(
                        "SELECT sum(like_count) FROM post WHERE author_id = ?", Long.class, me);
        assertThat(likes).isEqualTo(126);

        Instant before = Instant.now();
        mockMvc.perform(get("/api/me/withdrawal").cookie(TestLogin.loginAs(mockMvc, me)))
                .andExpect(status().isOk())
                .andExpect(
                        header().string(
                                        "Cache-Control",
                                        org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.handle").value("kim755030"))
                .andExpect(jsonPath("$.postCount").value(24))
                .andExpect(jsonPath("$.commentCount").value(18))
                .andExpect(jsonPath("$.receivedLikeCount").value(126))
                .andExpect(jsonPath("$.verification").value("PASSWORD"))
                .andExpect(
                        result -> {
                            String body = result.getResponse().getContentAsString();
                            Instant deadline =
                                    Instant.parse(
                                            com.jayway.jsonpath.JsonPath.read(
                                                    body, "$.restoreDeadline"));
                            assertThat(deadline)
                                    .isBetween(
                                            before.plus(Duration.ofDays(30)).minusSeconds(1),
                                            Instant.now().plus(Duration.ofDays(30)).plusSeconds(1));
                        });

        long social = members().member().provider("GITHUB").create();
        mockMvc.perform(get("/api/me/withdrawal").cookie(TestLogin.loginAs(mockMvc, social)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verification").value("CONFIRM_TEXT"))
                .andExpect(jsonPath("$.postCount").value(0));
    }

    @Test
    void US1_2_체크_없으면_400() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        withdraw(session, "{\"password\":\"" + PASSWORD + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WITHDRAW_CONFIRM_REQUIRED"))
                .andExpect(jsonPath("$.message").value("안내 내용을 확인하고 체크해 주세요"))
                .andExpect(jsonPath("$.errors").isArray());
        withdraw(session, "{\"confirmed\":false,\"password\":\"" + PASSWORD + "\"}")
                .andExpect(jsonPath("$.code").value("WITHDRAW_CONFIRM_REQUIRED"));
        assertThat(member(me).get("status")).isEqualTo("ACTIVE");
        // 확인 체크 판정은 본인 확인 전 — 실패 횟수를 쓰지 않는다
        assertThat(redis.hasKey("auth:pw-change-fail:" + me)).isFalse();
    }

    @Test
    void US1_3_비밀번호_5번_틀리면_15분() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        for (int i = 0; i < 5; i++) {
            withdraw(session, byPassword(WRONG))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_MISMATCH"))
                    .andExpect(jsonPath("$.errors[0].field").value("password"));
        }
        withdraw(session, byPassword(PASSWORD))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_TEMPORARILY_LOCKED"))
                .andExpect(header().exists("Retry-After"));
        assertThat(member(me).get("status")).isEqualTo("ACTIVE");
        // 비밀번호 변경 화면도 같은 잠금
        mockMvc.perform(
                        TestLogin.withCsrf(
                                post("/api/me/password")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"currentPassword\":\""
                                                        + PASSWORD
                                                        + "\",\"newPassword\":\"Fresh#2026b\","
                                                        + "\"newPasswordConfirm\":\"Fresh#2026b\"}"),
                                session))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void 비밀번호_변경_3번_탈퇴_2번_실패로도_잠긴다() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(
                            TestLogin.withCsrf(
                                    post("/api/me/password")
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(
                                                    "{\"currentPassword\":\""
                                                            + WRONG
                                                            + "\",\"newPassword\":\"Fresh#2026b\","
                                                            + "\"newPasswordConfirm\":\"Fresh#2026b\"}"),
                                    session))
                    .andExpect(status().isBadRequest());
        }
        withdraw(session, byPassword(WRONG)).andExpect(status().isBadRequest());
        withdraw(session, byPassword(WRONG)).andExpect(status().isBadRequest());
        withdraw(session, byPassword(PASSWORD)).andExpect(status().isTooManyRequests());
    }

    @Test
    void US1_4_소셜_탈퇴_문구_불일치() throws Exception {
        long me = members().member().provider("GOOGLE").email("g@gmail.com").create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        for (int i = 0; i < 6; i++) {
            withdraw(session, "{\"confirmed\":true,\"confirmText\":\"탈퇴함\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CONFIRM_TEXT_MISMATCH"))
                    .andExpect(jsonPath("$.message").value("'탈퇴'를 정확히 입력해 주세요"));
        }
        // 소셜은 비밀번호 칸을 무시하고, 실패 횟수를 세지 않는다
        withdraw(session, "{\"confirmed\":true,\"password\":\"" + PASSWORD + "\"}")
                .andExpect(jsonPath("$.code").value("CONFIRM_TEXT_MISMATCH"));
        assertThat(redis.hasKey("auth:pw-change-fail:" + me)).isFalse();
        assertThat(member(me).get("status")).isEqualTo("ACTIVE");

        withdraw(session, "{\"confirmed\":true,\"confirmText\":\"탈퇴 \"}")
                .andExpect(status().isOk());
        assertThat(member(me).get("status")).isEqualTo("WITHDRAWN");
    }

    @Test
    void US1_5_관리자_409() throws Exception {
        long admin = members().member().role("ADMIN").create();
        Cookie session = TestLogin.loginAs(mockMvc, admin);
        mockMvc.perform(get("/api/me/withdrawal").cookie(session)).andExpect(status().isOk());
        withdraw(session, byPassword(WRONG))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ADMIN_CANNOT_WITHDRAW"))
                .andExpect(jsonPath("$.message").value("관리자 권한을 해제한 뒤 탈퇴할 수 있어요"));
        assertThat(redis.hasKey("auth:pw-change-fail:" + admin)).isFalse();
    }

    @Test
    void US1_6_신청_즉시_모든_세션_끊김() throws Exception {
        long me = members().member().create();
        Cookie a = TestLogin.loginAs(mockMvc, me);
        Cookie b = TestLogin.loginAs(mockMvc, me);
        Instant before = Instant.now().truncatedTo(ChronoUnit.MICROS);

        MvcResult result =
                withdraw(a, byPassword(PASSWORD))
                        .andExpect(status().isOk())
                        .andExpect(
                                header().string(
                                                "Cache-Control",
                                                org.hamcrest.Matchers.containsString("no-store")))
                        .andExpect(jsonPath("$.restoreDeadline").exists())
                        .andReturn();

        mockMvc.perform(get("/api/me").cookie(a)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/me").cookie(b)).andExpect(status().isUnauthorized());
        Map<String, Object> row = member(me);
        assertThat(row.get("status")).isEqualTo("WITHDRAWN");
        Instant withdrawnAt = ((Timestamp) row.get("withdrawn_at")).toInstant();
        assertThat(withdrawnAt).isAfterOrEqualTo(before);
        Instant deadline =
                Instant.parse(
                        com.jayway.jsonpath.JsonPath.read(
                                result.getResponse().getContentAsString(), "$.restoreDeadline"));
        assertThat(deadline).isEqualTo(withdrawnAt.plus(Duration.ofDays(30)));
        assertThat(events.stream(MemberWithdrawn.class))
                .containsExactly(new MemberWithdrawn(me, withdrawnAt));
        assertThat(redis.hasKey("auth:pw-change-fail:" + me)).isFalse();
    }

    @Test
    void 인증_전_회원도_신청할_수_있다() throws Exception {
        long me = members().member().emailVerified(false).create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        mockMvc.perform(get("/api/me/withdrawal").cookie(session)).andExpect(status().isOk());
        withdraw(session, byPassword(PASSWORD)).andExpect(status().isOk());
    }

    @Test
    void 남은_세션의_정지_회원은_403() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        members().suspend(me, Instant.now().plus(1, ChronoUnit.DAYS), "스팸");
        mockMvc.perform(get("/api/me/withdrawal").cookie(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));
        withdraw(session, byPassword(PASSWORD))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));
    }

    @Test
    void 유예_회원의_안내와_재신청은_403() throws Exception {
        long me = members().member().status("WITHDRAWN").create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        mockMvc.perform(get("/api/me/withdrawal").cookie(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_WITHDRAWN"));
        withdraw(session, byPassword(PASSWORD))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_WITHDRAWN"));
    }

    @Test
    void 비로그인은_401() throws Exception {
        mockMvc.perform(get("/api/me/withdrawal")).andExpect(status().isUnauthorized());
        withdraw(null, byPassword(PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_REQUIRED"));
    }

    @Test
    void 동시에_두_번_신청하면_하나만_200() throws Exception {
        long me = members().member().create();
        Cookie a = TestLogin.loginAs(mockMvc, me);
        Cookie b = TestLogin.loginAs(mockMvc, me);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (Cookie c : List.of(a, b)) {
                results.add(
                        pool.submit(
                                () -> {
                                    start.await();
                                    return withdraw(c, byPassword(PASSWORD))
                                            .andReturn()
                                            .getResponse()
                                            .getStatus();
                                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : results) {
                statuses.add(f.get());
            }
            assertThat(statuses).containsOnlyOnce(200);
            assertThat(statuses).allMatch(s -> s == 200 || s == 401 || s == 403);
        } finally {
            pool.shutdownNow();
        }
        assertThat(events.stream(MemberWithdrawn.class)).hasSize(1);
    }

    @Test
    void Redis_정지_중이면_503이고_상태는_그대로() {
        long me = members().member().create();
        // HTTP로는 Redis가 멈추면 세션을 읽지 못해 401이 된다 — 세션 삭제 단계의 장애는 서비스를 직접 불러 재현한다
        try (RedisOutage outage = RedisOutage.start()) {
            assertThatThrownBy(
                            () ->
                                    withdrawalService.withdraw(
                                            me, new WithdrawCommand(true, PASSWORD, null)))
                    .isInstanceOf(TemporarilyUnavailableException.class);
        }
        assertThat(member(me).get("status")).isEqualTo("ACTIVE");
        assertThat(member(me).get("withdrawn_at")).isNull();
        assertThat(events.stream(MemberWithdrawn.class)).isEmpty();
    }

    @Test
    void 사유_칸을_보내도_저장하지_않는다() throws Exception {
        long me = members().member().create();
        withdraw(
                        TestLogin.loginAs(mockMvc, me),
                        "{\"confirmed\":true,\"password\":\""
                                + PASSWORD
                                + "\",\"reason\":\"그냥요\",\"reasonDetail\":\"비밀\"}")
                .andExpect(status().isOk());
        List<String> columns =
                jdbc.queryForList(
                        "SELECT column_name FROM information_schema.columns WHERE table_name ="
                                + " 'member' AND column_name LIKE '%reason%'",
                        String.class);
        assertThat(columns).isEmpty();
    }

    @Test
    void 로그에_비밀번호와_이메일이_없다(CapturedOutput output) throws Exception {
        long me = members().member().email("secret-mail@example.com").create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        withdraw(session, byPassword(WRONG)).andExpect(status().isBadRequest());
        withdraw(session, byPassword(PASSWORD)).andExpect(status().isOk());
        assertThat(output.getAll())
                .contains("탈퇴 신청 memberId=" + me)
                .doesNotContain(PASSWORD)
                .doesNotContain(WRONG)
                .doesNotContain("secret-mail@example.com");
    }
}
