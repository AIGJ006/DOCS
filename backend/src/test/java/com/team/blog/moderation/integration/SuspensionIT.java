package com.team.blog.moderation.integration;

import static com.team.blog.moderation.support.ReportApi.read;
import static com.team.blog.moderation.support.ReportApi.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.team.blog.account.application.SuspensionDuration;
import com.team.blog.account.application.SuspensionService;
import com.team.blog.discovery.support.ReadingApi;
import com.team.blog.moderation.support.ModerationEventRecorder;
import com.team.blog.moderation.support.ReportApi;
import com.team.blog.shared.error.TemporarilyUnavailableException;
import com.team.blog.shared.event.MemberSuspended;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.MemberFixtures;
import com.team.blog.support.RedisOutage;
import com.team.blog.support.TestLogin;
import com.team.blog.support.fixture.PostFixtures;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/** 회원 정지 (014 T050, US5, quickstart §2 {@code SuspensionIT}). */
class SuspensionIT extends IntegrationTestBase {

    @Autowired ModerationEventRecorder events;
    @Autowired SuspensionService suspensions;

    private ReportApi api;
    private long adminId;
    private Cookie admin;

    @BeforeEach
    void setUp() {
        api = new ReportApi(mockMvc);
        adminId = members().member().role("ADMIN").handle("admin001").create();
        admin = TestLogin.loginAs(mockMvc, adminId);
        events.clear();
    }

    private int status(MvcResult r) {
        return ReportApi.status(r);
    }

    private MvcResult login(String email) throws Exception {
        return mockMvc.perform(
                        TestLogin.withCsrf(
                                post("/api/auth/login")
                                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                        .param("email", email)
                                        .param("password", MemberFixtures.DEFAULT_PASSWORD),
                                null))
                .andReturn();
    }

    @Test
    void 칠일_정지는_세션을_모두_끊고_로그인을_막는다() throws Exception {
        long id = members().member().handle("bad00001").email("bad@example.com").create();
        List<Cookie> sessions = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            sessions.add(TestLogin.loginAs(mockMvc, id));
        }
        long postId = new PostFixtures(jdbc).create(id, PostFixtures.State.PUBLISHED_PUBLIC);

        MvcResult r = api.suspend(admin, "bad00001", "P7D", "스팸 글 반복 게시");
        assertThat(status(r)).isEqualTo(201);
        assertThat((String) read(r, "$.status")).isEqualTo("SUSPENDED");
        assertThat((String) read(r, "$.openSuspension.reason")).isEqualTo("스팸 글 반복 게시");
        assertThat((String) read(r, "$.openSuspension.suspendedByHandle")).isEqualTo("admin001");
        Instant endsAt = Instant.parse(read(r, "$.openSuspension.endsAt"));
        assertThat(endsAt)
                .isCloseTo(Instant.now().plus(Duration.ofDays(7)), within(Duration.ofMinutes(1)));

        for (Cookie session : sessions) {
            assertThat(
                            mockMvc.perform(get("/api/me").cookie(session))
                                    .andReturn()
                                    .getResponse()
                                    .getStatus())
                    .isEqualTo(401);
        }
        MvcResult denied = login("bad@example.com");
        assertThat(status(denied)).isEqualTo(403);
        assertThat((String) read(denied, "$.code")).isEqualTo("ACCOUNT_SUSPENDED");
        assertThat((String) read(denied, "$.details.reason")).isEqualTo("스팸 글 반복 게시");

        assertThat(ReadingApi.status(new ReadingApi(mockMvc).detail(null, postId)))
                .as("정지 회원의 글은 그대로 보인다")
                .isEqualTo(200);
        assertThat(events.of(MemberSuspended.class))
                .singleElement()
                .satisfies(e -> assertThat(e.memberId()).isEqualTo(id));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification", Integer.class))
                .isZero();
    }

    private static org.assertj.core.data.TemporalUnitOffset within(Duration d) {
        return new org.assertj.core.data.TemporalUnitWithinOffset(
                d.toSeconds(), ChronoUnit.SECONDS);
    }

    @Test
    void 영구_정지는_끝_시각이_없다() throws Exception {
        members().member().handle("perm0001").create();
        MvcResult r = api.suspend(admin, "perm0001", "PERMANENT", "반복 위반");
        assertThat(status(r)).isEqualTo(201);
        assertThat((Object) read(r, "$.openSuspension.endsAt")).isNull();
    }

    @Test
    void 기한이_지난_정지는_다음_로그인_때_풀린다() throws Exception {
        long id = members().member().handle("late0001").email("late@example.com").create();
        members().suspend(id, Instant.now().minus(Duration.ofMinutes(1)), "지난 정지");

        MvcResult r = login("late@example.com");
        assertThat(status(r)).isNotEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT status FROM member WHERE id = ?", String.class, id))
                .isEqualTo("ACTIVE");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT lifted_at IS NOT NULL AND lifted_by IS NULL FROM"
                                        + " member_suspension WHERE member_id = ?",
                                Boolean.class,
                                id))
                .isTrue();
    }

    @Test
    void 관리자_사유_없음_탈퇴_유예는_400_이미_정지면_409() throws Exception {
        members().member().handle("admin002").role("ADMIN").create();
        long leaving = members().member().handle("leave001").create();
        new PostFixtures(jdbc).withdraw(leaving);
        members().member().handle("plain001").create();

        MvcResult adminTarget = api.suspend(admin, "admin002", "P1D", "사유");
        assertThat(status(adminTarget)).isEqualTo(400);
        assertThat((String) read(adminTarget, "$.code")).isEqualTo("CANNOT_SUSPEND_ADMIN");
        MvcResult withdrawn = api.suspend(admin, "leave001", "P1D", "사유");
        assertThat(status(withdrawn)).isEqualTo(400);
        assertThat((String) read(withdrawn, "$.code")).isEqualTo("CANNOT_SUSPEND_WITHDRAWN");
        MvcResult noReason = api.suspend(admin, "plain001", "P1D", "  ");
        assertThat(status(noReason)).isEqualTo(400);
        assertThat((String) read(noReason, "$.errors[0].field")).isEqualTo("reason");
        MvcResult badDuration = api.suspend(admin, "plain001", "P3D", "사유");
        assertThat(status(badDuration)).isEqualTo(400);
        MvcResult tooLong = api.suspend(admin, "plain001", "P1D", "가".repeat(201));
        assertThat(status(tooLong)).isEqualTo(400);

        assertThat(status(api.suspend(admin, "plain001", "P1D", "첫 정지"))).isEqualTo(201);
        MvcResult again = api.suspend(admin, "plain001", "P7D", "다시");
        assertThat(status(again)).isEqualTo(409);
        assertThat((String) read(again, "$.code")).isEqualTo("ALREADY_SUSPENDED");
        assertThat(status(api.suspend(admin, "nobody99", "P1D", "사유"))).isEqualTo(404);
    }

    @Test
    void 동시에_정지하면_열린_정지는_하나() throws Exception {
        long id = members().member().handle("race0001").create();
        long admin2 = members().member().role("ADMIN").create();
        List<Cookie> admins = List.of(admin, TestLogin.loginAs(mockMvc, admin2));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (Cookie session : admins) {
                results.add(
                        pool.submit(
                                () -> {
                                    start.await();
                                    return status(api.suspend(session, "race0001", "P1D", "동시"));
                                }));
            }
            start.countDown();
            List<Integer> codes = new ArrayList<>();
            for (Future<Integer> f : results) {
                codes.add(f.get(30, TimeUnit.SECONDS));
            }
            assertThat(codes).containsExactlyInAnyOrder(201, 409);
        } finally {
            pool.shutdownNow();
        }
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM member_suspension WHERE member_id = ? AND"
                                        + " lifted_at IS NULL",
                                Integer.class,
                                id))
                .isEqualTo(1);
    }

    @Test
    void 세션_저장소가_멈추면_정지하지_않고_503() {
        long id = members().member().create();
        try (RedisOutage outage = RedisOutage.start()) {
            assertThatThrownBy(
                            () ->
                                    suspensions.suspend(
                                            id,
                                            "장애 중",
                                            SuspensionDuration.P1D,
                                            adminId,
                                            Instant.now()))
                    .isInstanceOf(TemporarilyUnavailableException.class);
        }
        assertThat(jdbc.queryForObject("SELECT status FROM member WHERE id = ?", String.class, id))
                .isEqualTo("ACTIVE");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM member_suspension WHERE member_id = ?",
                                Integer.class,
                                id))
                .isZero();
        assertThat(events.of(MemberSuspended.class)).isEmpty();
    }

    @Test
    void 해제는_해제한_관리자를_남기고_ACTIVE로() throws Exception {
        long id = members().member().handle("lift0001").create();
        assertThat(status(api.suspend(admin, "lift0001", "P30D", "잠시"))).isEqualTo(201);
        events.clear();

        MvcResult r = api.lift(admin, "lift0001");
        assertThat(status(r)).isEqualTo(200);
        assertThat((String) read(r, "$.status")).isEqualTo("ACTIVE");
        assertThat((Object) read(r, "$.openSuspension")).isNull();
        assertThat((String) read(r, "$.history[0].liftedByHandle")).isEqualTo("admin001");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT lifted_by FROM member_suspension WHERE member_id = ?",
                                Long.class,
                                id))
                .isEqualTo(adminId);
        assertThat(status(api.lift(admin, "lift0001"))).as("열린 정지가 없어도 200").isEqualTo(200);
        assertThat(events.all()).isEmpty();

        MvcResult view = api.member(admin, "lift0001");
        assertThat((Integer) read(view, "$.history.length()")).isEqualTo(1);
    }
}
