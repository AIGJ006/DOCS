package com.team.blog.shared.security.integration;

import static com.team.blog.post.support.VisibilityApi.body;
import static com.team.blog.post.support.VisibilityApi.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

import com.team.blog.account.application.SessionTerminator;
import com.team.blog.post.support.TrashApi;
import com.team.blog.post.support.VisibilityApi;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import com.team.blog.support.fixture.PostFixtures;
import com.team.blog.support.fixture.PostFixtures.State;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * 계정 상태에 맞는 일관된 거부 (004 T049·T054, US4 인수 1~6, FR-028~FR-032, SC-006·SC-008, 42 §3). 판정 순서는 ①
 * 로그인(401) → ② 계정 상태(403) → ③④ 대상·권한(404) → ⑤ 규칙(400)이라, 비회원·인증 전·탈퇴 유예·정지 회원의 응답은 대상 글이 있든 없든
 * 바이트까지 같다.
 */
class AccountStateIT extends IntegrationTestBase {

    private static final String LOGIN_REQUIRED =
            "{\"code\":\"LOGIN_REQUIRED\",\"message\":\"로그인이 필요해요\",\"errors\":[],\"details\":null}";

    @Autowired private SessionTerminator sessionTerminator;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    private PostFixtures posts;
    private VisibilityApi api;

    @BeforeEach
    void setUp() {
        posts = new PostFixtures(jdbc);
        api = new VisibilityApi(mockMvc);
    }

    /** 같은 행위자가 자기 글·남의 비공개 글·없는 번호에 보낸 공개 범위 변경 응답 (상태·본문·Cache-Control). */
    private record Answer(int status, String body, String cacheControl) {
        static Answer of(MvcResult result) {
            return new Answer(
                    VisibilityApi.status(result),
                    VisibilityApi.body(result),
                    VisibilityApi.cacheControl(result));
        }
    }

    private void assertSameForAnyTarget(Cookie session, long ownPost, Answer expected)
            throws Exception {
        long othersPrivate = posts.create(members().member().create(), State.PUBLISHED_PRIVATE);
        assertThat(Answer.of(api.change(session, ownPost, "PRIVATE")))
                .as("자기 글")
                .isEqualTo(expected);
        assertThat(Answer.of(api.change(session, othersPrivate, "PRIVATE")))
                .as("남의 비공개 글")
                .isEqualTo(expected);
        assertThat(Answer.of(api.change(session, posts.nonexistentId(), "PRIVATE")))
                .as("없는 글")
                .isEqualTo(expected);
        assertThat(Answer.of(api.change(session, ownPost, "FRIENDS")))
                .as("허용되지 않는 값보다 먼저")
                .isEqualTo(expected);
    }

    private String visibilityOf(long postId) {
        return jdbc.queryForObject(
                "SELECT visibility FROM post WHERE id = ?", String.class, postId);
    }

    @Test
    void US4_1_비회원은_401_LOGIN_REQUIRED_대상과_무관() throws Exception {
        long postId = posts.create(members().member().create(), State.PUBLISHED_PRIVATE);

        MvcResult result = api.change(null, postId, "PUBLIC");

        assertThat(status(result)).isEqualTo(401);
        assertThat(body(result)).isEqualTo(LOGIN_REQUIRED);
        assertThat(Answer.of(api.change(null, posts.nonexistentId(), "PUBLIC")))
                .isEqualTo(Answer.of(result));
        assertThat(Answer.of(api.send(null, postId, "{}")))
                .as("본문 오류보다 먼저")
                .isEqualTo(Answer.of(result));
    }

    @Test
    void US4_2_US4_6_인증_전_회원은_403_EMAIL_NOT_VERIFIED_있는_글_없는_글_같음() throws Exception {
        long me = members().member().emailVerified(false).create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long mine = posts.create(me, State.PUBLISHED_PUBLIC);

        Answer expected = Answer.of(api.change(session, mine, "PRIVATE"));

        assertThat(expected.status()).isEqualTo(403);
        assertThat(expected.body())
                .isEqualTo(
                        "{\"code\":\"EMAIL_NOT_VERIFIED\",\"message\":\"이메일 인증 후 이용할 수 있어요\","
                                + "\"errors\":[],\"details\":{\"action\":\"RESEND_VERIFICATION\"}}");
        assertSameForAnyTarget(session, mine, expected);
        assertThat(visibilityOf(mine)).isEqualTo("PUBLIC");
    }

    @Test
    void US4_3_인증_전_작성자도_자기_글_휴지통_이동과_복구는_된다() throws Exception {
        long me = members().member().emailVerified(false).create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long mine = posts.create(me, State.PUBLISHED_PUBLIC);
        TrashApi trash = new TrashApi(mockMvc);

        assertThat(TrashApi.status(trash.trash(session, mine))).isEqualTo(200);
        assertThat(TrashApi.status(trash.restore(session, mine))).isEqualTo(200);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT deleted_at IS NULL FROM post WHERE id = ?",
                                Boolean.class,
                                mine))
                .isTrue();
    }

    @Test
    void 인증_전_회원도_기본_공개_범위는_바꿀_수_있다_FR_032() throws Exception {
        boolean exists;
        try {
            exists =
                    handlerMapping.getHandler(
                                    new MockHttpServletRequest("PATCH", "/api/me/settings"))
                            != null;
        } catch (Exception e) {
            exists = false;
        }
        Assumptions.assumeTrue(exists, "pending: 001 (PATCH /api/me/settings, 001 T118)");

        long me = members().member().emailVerified(false).create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        MvcResult result =
                mockMvc.perform(
                                TestLogin.withCsrf(patch("/api/me/settings"), session)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"defaultVisibility\":\"PRIVATE\"}"))
                        .andReturn();

        assertThat(status(result)).isEqualTo(200);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT default_visibility FROM member WHERE id = ?",
                                String.class,
                                me))
                .isEqualTo("PRIVATE");
    }

    @Test
    void US4_4_탈퇴_유예_회원은_403_ACCOUNT_WITHDRAWN_RESTORE() throws Exception {
        long me = members().member().create();
        long mine = posts.create(me, State.PUBLISHED_PUBLIC);
        Cookie session = TestLogin.loginAs(mockMvc, me);
        posts.withdraw(me);

        Answer expected = Answer.of(api.change(session, mine, "PRIVATE"));

        assertThat(expected.status()).isEqualTo(403);
        assertThat(expected.body()).contains("\"code\":\"ACCOUNT_WITHDRAWN\"");
        assertThat(expected.body()).contains("\"details\":{\"action\":\"RESTORE\"}");
        assertSameForAnyTarget(session, mine, expected);
        assertThat(visibilityOf(mine)).isEqualTo("PUBLIC");
    }

    @Test
    void H7_정지_직후_남은_세션의_쓰기는_403_ACCOUNT_SUSPENDED() throws Exception {
        long me = members().member().create();
        long mine = posts.create(me, State.PUBLISHED_PUBLIC);
        Cookie session = TestLogin.loginAs(mockMvc, me);
        assertThat(status(api.change(session, mine, "PRIVATE"))).isEqualTo(200);

        members().suspend(me, Instant.now().plus(Duration.ofDays(7)), "권한 시험");

        Answer expected = Answer.of(api.change(session, mine, "PUBLIC"));
        // 001이 정지할 때 세션을 지우면 401, 남겨 두면 계정 상태 검사가 403 — 어느 쪽이든 바뀌지 않는다
        assertThat(expected.status()).isIn(401, 403);
        if (expected.status() == 403) {
            assertThat(expected.body())
                    .contains("\"code\":\"ACCOUNT_SUSPENDED\"")
                    .contains("\"details\":null");
            assertSameForAnyTarget(session, mine, expected);
        }
        assertThat(visibilityOf(mine)).isEqualTo("PRIVATE");
    }

    @Test
    void US4_5_모든_세션_종료_후_두_기기_모두_401() throws Exception {
        long me = members().member().create();
        long mine = posts.create(me, State.PUBLISHED_PUBLIC);
        Cookie phone = TestLogin.loginAs(mockMvc, me);
        Cookie laptop = TestLogin.loginAs(mockMvc, me);
        assertThat(status(api.change(phone, mine, "PRIVATE"))).isEqualTo(200);
        assertThat(status(api.change(laptop, mine, "PUBLIC"))).isEqualTo(200);

        sessionTerminator.terminateAll(me, Optional.empty());

        for (Cookie session : new Cookie[] {phone, laptop}) {
            MvcResult result = api.change(session, mine, "PRIVATE");
            assertThat(status(result)).isEqualTo(401);
            assertThat(body(result)).isEqualTo(LOGIN_REQUIRED);
        }
        assertThat(visibilityOf(mine)).isEqualTo("PUBLIC");
    }

    @Test
    void 판정_순서_계정_상태가_대상_조회와_값_검사보다_먼저_T054() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        members().suspend(me, Instant.now().plus(Duration.ofDays(1)), "순서 시험");

        // 없는 글 + 허용되지 않는 값이어도 ② 계정 상태가 먼저
        MvcResult result = api.change(session, posts.nonexistentId(), "FRIENDS");

        assertThat(status(result)).isIn(401, 403);
        assertThat(body(result)).doesNotContain("NOT_FOUND").doesNotContain("INVALID_VISIBILITY");
    }
}
