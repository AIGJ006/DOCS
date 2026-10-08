package com.team.blog.interaction.integration;

import static com.team.blog.interaction.support.FollowApi.body;
import static com.team.blog.interaction.support.FollowApi.read;
import static com.team.blog.interaction.support.FollowApi.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.team.blog.account.domain.MemberStatus;
import com.team.blog.account.domain.Role;
import com.team.blog.interaction.application.FollowService;
import com.team.blog.interaction.support.FollowApi;
import com.team.blog.interaction.support.FollowEventProbe;
import com.team.blog.interaction.support.FollowFixtures;
import com.team.blog.shared.event.MemberFollowed;
import com.team.blog.shared.event.MemberUnfollowed;
import com.team.blog.shared.security.Viewer;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.RedisOutage;
import com.team.blog.support.TestLogin;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/** 팔로우·언팔로우 API (010 T013 US1, contracts {@code followMember}·{@code unfollowMember}). */
class FollowApiIT extends IntegrationTestBase {

    @Autowired FollowEventProbe probe;
    @Autowired FollowService followService;

    private long me;
    private long target;
    private String targetHandle;
    private Cookie session;

    @BeforeEach
    void setUp() {
        me = members().member().create();
        target = members().member().handle("target_b").create();
        targetHandle = "target_b";
        session = TestLogin.loginAs(mockMvc, me);
        probe.arm();
    }

    @AfterEach
    void tearDown() {
        probe.reset();
    }

    private FollowApi api() {
        return new FollowApi(mockMvc);
    }

    private FollowFixtures fixtures() {
        return new FollowFixtures(jdbc);
    }

    private static void assertState(MvcResult result, boolean following, int count) {
        assertThat(status(result)).as(body(result)).isEqualTo(200);
        assertThat((Boolean) read(result, "$.following")).isEqualTo(following);
        assertThat(((Number) read(result, "$.followerCount")).intValue()).isEqualTo(count);
    }

    private static void assertError(MvcResult result, int status, String code) {
        assertThat(status(result)).as(body(result)).isEqualTo(status);
        assertThat((String) read(result, "$.code")).isEqualTo(code);
        assertThat((List<?>) read(result, "$.errors")).isEmpty();
    }

    @Test
    void US1_1_팔로우_응답() throws Exception {
        MvcResult result = api().follow(session, targetHandle);

        assertState(result, true, 1);
        assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("private, no-store");
        assertThat(fixtures().rows(me, target)).isEqualTo(1);
        assertThat(probe.events()).hasSize(1);
        MemberFollowed event = (MemberFollowed) probe.events().get(0);
        assertThat(event.followerId()).isEqualTo(me);
        assertThat(event.followeeId()).isEqualTo(target);
        assertThat(event.followedAt()).isNotNull();
    }

    @Test
    void 이미_팔로우_중이면_200_변화_이벤트_없음() throws Exception {
        assertState(api().follow(session, targetHandle), true, 1);
        assertState(api().follow(session, targetHandle), true, 1);
        assertThat(probe.followed()).isEqualTo(1);
        assertThat(fixtures().rows(me, target)).isEqualTo(1);
    }

    @Test
    void US1_2_언팔로우() throws Exception {
        long other = members().member().create();
        fixtures().follow(other, target);
        api().follow(session, targetHandle);

        MvcResult result = api().unfollow(session, targetHandle);

        assertState(result, false, 1);
        assertThat(fixtures().rows(me, target)).isZero();
        assertThat(probe.unfollowed()).isEqualTo(1);
        MemberUnfollowed event =
                (MemberUnfollowed)
                        probe.events().stream()
                                .filter(MemberUnfollowed.class::isInstance)
                                .findFirst()
                                .orElseThrow();
        assertThat(event.followerId()).isEqualTo(me);
        assertThat(event.followeeId()).isEqualTo(target);
        assertThat(event.unfollowedAt()).isNotNull();
    }

    @Test
    void US1_4_안_한_상태_언팔로우_200_변화없음() throws Exception {
        assertState(api().unfollow(session, targetHandle), false, 0);
        assertThat(probe.events()).isEmpty();
    }

    @Test
    void 응답_수는_다른_사람_변화와_유예_제외를_반영() throws Exception {
        for (int i = 0; i < 3; i++) {
            fixtures().follow(members().member().create(), target);
        }
        long withdrawn = members().member().create();
        fixtures().follow(withdrawn, target);
        fixtures().withdraw(withdrawn);

        assertState(api().follow(session, targetHandle), true, 4);
    }

    @Test
    void US1_5_자기자신_400() throws Exception {
        String myHandle = fixtures().handleOf(me);
        MvcResult result = api().follow(session, myHandle);

        assertError(result, 400, "CANNOT_FOLLOW_SELF");
        assertThat((String) read(result, "$.message")).isEqualTo("자기 자신은 팔로우할 수 없어요");
        assertThat(read(result, "$.details") == null).isTrue();
        assertError(api().unfollow(session, myHandle), 400, "CANNOT_FOLLOW_SELF");
        assertThat(fixtures().rowsOf(me)).isZero();
        assertThat(probe.events()).isEmpty();
    }

    @Test
    void US1_6_비회원_401() throws Exception {
        assertError(api().follow(null, targetHandle), 401, "LOGIN_REQUIRED");
        assertError(api().unfollow(null, targetHandle), 401, "LOGIN_REQUIRED");
        assertThat(fixtures().rowsOf(target)).isZero();
    }

    @Test
    void US1_7_인증전_200() throws Exception {
        long unverified = members().member().emailVerified(false).create();
        assertState(api().follow(TestLogin.loginAs(mockMvc, unverified), targetHandle), true, 1);
    }

    @Test
    void US1_8_없는주소_유예회원_404() throws Exception {
        long withdrawn = members().member().handle("gone_c").create();
        fixtures().withdraw(withdrawn);
        long anonymized =
                members().member().handle("anon_d").status("WITHDRAWN").deleted().create();

        MvcResult missing = api().follow(session, "nobody_here");
        MvcResult pending = api().follow(session, "gone_c");
        MvcResult erased = api().follow(session, "anon_d");
        MvcResult upper = api().follow(session, targetHandle.toUpperCase(Locale.ROOT));
        MvcResult unfollowPending = api().unfollow(session, "gone_c");

        for (MvcResult result : List.of(missing, pending, erased, upper, unfollowPending)) {
            assertError(result, 404, "NOT_FOUND");
            assertThat(result.getResponse().getContentAsByteArray())
                    .isEqualTo(missing.getResponse().getContentAsByteArray());
        }
        assertThat((String) read(missing, "$.message")).isEqualTo("볼 수 없는 페이지예요");
        assertThat(fixtures().rowsOf(withdrawn)).isZero();
        assertThat(fixtures().rowsOf(anonymized)).isZero();
        assertThat(probe.events()).isEmpty();
    }

    @Test
    void 유예_회원_본인은_403_ACCOUNT_WITHDRAWN() throws Exception {
        long withdrawn = members().member().status("WITHDRAWN").create();
        Cookie withdrawnSession = TestLogin.loginAs(mockMvc, withdrawn);

        assertError(api().follow(withdrawnSession, targetHandle), 403, "ACCOUNT_WITHDRAWN");
        // 판정 순서: 유예 회원이 자기 자신을 팔로우 → 400이 아니라 403
        assertError(
                api().follow(withdrawnSession, fixtures().handleOf(withdrawn)),
                403,
                "ACCOUNT_WITHDRAWN");
        assertThat(fixtures().rowsOf(withdrawn)).isZero();
    }

    @Test
    void 남은_세션의_정지_회원은_403_ACCOUNT_SUSPENDED() throws Exception {
        members().suspend(me, Instant.now().plus(Duration.ofDays(7)), "시험");

        assertError(api().follow(session, targetHandle), 403, "ACCOUNT_SUSPENDED");
        assertThat(fixtures().rowsOf(me)).isZero();
    }

    @Test
    void 정지된_대상은_팔로우할_수_있다() throws Exception {
        members().suspend(target, Instant.now().plus(Duration.ofDays(7)), "시험");
        assertState(api().follow(session, targetHandle), true, 1);
    }

    @Test
    void 관리자도_일반_회원과_같다() throws Exception {
        long admin = members().member().role("ADMIN").handle("admin_x").create();
        Cookie adminSession = TestLogin.loginAs(mockMvc, admin);

        assertState(api().follow(adminSession, targetHandle), true, 1);
        assertError(api().follow(adminSession, "admin_x"), 400, "CANNOT_FOLLOW_SELF");
        fixtures().withdraw(target);
        assertError(api().unfollow(adminSession, targetHandle), 404, "NOT_FOUND");
    }

    @Test
    void 팔로우_언팔로우_합쳐_31번째는_429와_Retry_After() throws Exception {
        for (int i = 0; i < 30; i++) {
            MvcResult result =
                    i % 2 == 0
                            ? api().follow(session, targetHandle)
                            : api().unfollow(session, targetHandle);
            assertThat(status(result)).as("요청 " + (i + 1)).isEqualTo(200);
        }

        MvcResult result = api().follow(session, targetHandle);

        assertError(result, 429, "TOO_MANY_REQUESTS");
        assertThat(Integer.parseInt(result.getResponse().getHeader("Retry-After")))
                .isBetween(1, 60);
        assertThat(fixtures().rows(me, target)).isZero();
        // 판정 순서: 요청 횟수를 넘긴 회원도 없는 주소면 404, 자기 자신이면 400 (429는 맨 끝)
        assertError(api().follow(session, "nobody_here"), 404, "NOT_FOUND");
        assertError(api().follow(session, fixtures().handleOf(me)), 400, "CANNOT_FOLLOW_SELF");
    }

    @Test
    void 앞_단계에서_걸린_요청은_세지_않는다() throws Exception {
        for (int i = 0; i < 40; i++) {
            assertThat(status(api().follow(session, "nobody_here"))).isEqualTo(404);
            assertThat(status(api().follow(session, fixtures().handleOf(me)))).isEqualTo(400);
        }
        assertThat(redis.hasKey(FollowService.RATE_LIMIT_PREFIX + me)).isFalse();
        assertState(api().follow(session, targetHandle), true, 1);
    }

    @Test
    void Redis_정지_중에는_제한_없이_200() {
        // 세션이 Redis에 있어 장애 중 HTTP 요청은 비회원이 되므로 서비스를 직접 부른다 (009 T044와 같은 방식)
        Viewer viewer = new Viewer(me, Role.USER, MemberStatus.ACTIVE, true);
        try (RedisOutage outage = RedisOutage.start()) {
            for (int i = 0; i < 32; i++) {
                FollowService.FollowState state =
                        i % 2 == 0
                                ? followService.follow(viewer, targetHandle)
                                : followService.unfollow(viewer, targetHandle);
                assertThat(state.following()).isEqualTo(i % 2 == 0);
            }
        }
        assertThat(fixtures().rows(me, target)).isZero();
    }

    @Test
    void CSRF_헤더가_없으면_403() throws Exception {
        MvcResult result =
                mockMvc.perform(put("/api/members/{h}/follow", targetHandle).cookie(session))
                        .andReturn();
        assertError(result, 403, "CSRF_REJECTED");
        assertThat(fixtures().rowsOf(me)).isZero();
    }
}
