package com.team.blog.post.web;

import static com.team.blog.post.support.VisibilityApi.body;
import static com.team.blog.post.support.VisibilityApi.cacheControl;
import static com.team.blog.post.support.VisibilityApi.read;
import static com.team.blog.post.support.VisibilityApi.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.post.support.VisibilityApi;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import com.team.blog.support.fixture.PostFixtures;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code PUT /api/posts/{postId}/visibility} 계약 (004 T027, contracts/openapi.yaml {@code
 * setPostVisibility}). 응답 본문의 키·형식·헤더를 계약의 {@code VisibilityChangeResponse}·{@code
 * ErrorResponse}·{@code LoginRequired}·{@code AccountStateDenied}·{@code NotFound}와 견준다.
 *
 * <p>(구현 메모) 서비스를 가짜로 바꾸는 {@code @WebMvcTest} 대신 실제 DB·세션을 쓰는 통합 테스트로 둔다 — 새 {@code @MockitoBean}
 * 조합·테스트 컨텍스트를 늘리지 않기 위해서다. 그래서 이름이 {@code *ContractIT}다.
 */
class PostVisibilityControllerContractIT extends IntegrationTestBase {

    private static final String LOGIN_REQUIRED =
            "{\"code\":\"LOGIN_REQUIRED\",\"message\":\"로그인이 필요해요\",\"errors\":[],\"details\":null}";
    private static final String NOT_FOUND =
            "{\"code\":\"NOT_FOUND\",\"message\":\"볼 수 없는 페이지예요\",\"errors\":[],\"details\":null}";

    private final JsonMapper json = JsonMapper.builder().build();
    private PostFixtures posts;
    private VisibilityApi api;
    private long author;
    private Cookie session;

    @BeforeEach
    void setUp() {
        posts = new PostFixtures(jdbc);
        api = new VisibilityApi(mockMvc);
        author = members().member().create();
        session = TestLogin.loginAs(mockMvc, author);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(MvcResult result) throws Exception {
        return json.readValue(body(result), Map.class);
    }

    @Test
    void 성공_200은_visibility와_firstPublicAt만_있고_no_store() throws Exception {
        long postId = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        Instant stored =
                jdbc.queryForObject(
                                "SELECT first_public_at FROM post WHERE id = ?",
                                java.sql.Timestamp.class,
                                postId)
                        .toInstant();

        MvcResult result = api.change(session, postId, "PRIVATE");

        assertThat(status(result)).isEqualTo(200);
        assertThat(result.getResponse().getContentType()).startsWith("application/json");
        assertThat(cacheControl(result)).isEqualTo("private, no-store");
        assertThat(map(result).keySet()).containsExactlyInAnyOrder("visibility", "firstPublicAt");
        assertThat((String) read(result, "$.visibility")).isEqualTo("PRIVATE");
        String firstPublicAt = read(result, "$.firstPublicAt");
        assertThat(firstPublicAt).endsWith("Z");
        assertThat(Instant.parse(firstPublicAt)).isEqualTo(stored);
        assertThat(Instant.parse(firstPublicAt))
                .isEqualTo(Instant.parse(firstPublicAt).truncatedTo(ChronoUnit.MICROS));
    }

    @Test
    void 성공_200_임시글은_firstPublicAt이_null() throws Exception {
        long postId = posts.create(author, PostFixtures.State.DRAFT);

        MvcResult result = api.change(session, postId, "PRIVATE");

        assertThat(status(result)).isEqualTo(200);
        assertThat(map(result)).containsEntry("firstPublicAt", null);
        assertThat(map(result).keySet()).containsExactlyInAnyOrder("visibility", "firstPublicAt");
    }

    @Test
    void 잘못된_값_400_INVALID_VISIBILITY_본문() throws Exception {
        long postId = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);

        MvcResult result = api.change(session, postId, "FRIENDS");

        assertThat(status(result)).isEqualTo(400);
        assertThat(body(result))
                .isEqualTo(
                        "{\"code\":\"INVALID_VISIBILITY\",\"message\":\"공개 범위를 다시 선택해 주세요\","
                                + "\"errors\":[{\"field\":\"visibility\",\"code\":\"INVALID_VISIBILITY\","
                                + "\"message\":\"허용되지 않은 공개 범위예요\"}],\"details\":null}");
    }

    /**
     * (구현 메모) 본문 형식 오류 코드는 001 {@code GlobalExceptionHandler}가 이미 정한 {@code MALFORMED_REQUEST}를 쓴다
     * — 004 계약의 제안 코드 {@code INVALID_REQUEST}와 같은 뜻이며 팀 결정 대기(ANALYSIS-tier-a R3).
     */
    @Test
    void 본문_없음과_JSON_아님은_400_형식_오류() throws Exception {
        long postId = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);

        for (String raw : new String[] {null, "", "visibility=PRIVATE", "{\"visibility\":"}) {
            MvcResult result = api.send(session, postId, raw);
            assertThat(status(result)).as(String.valueOf(raw)).isEqualTo(400);
            assertThat((String) read(result, "$.code")).isEqualTo("MALFORMED_REQUEST");
            assertThat(map(result).keySet())
                    .containsExactly("code", "message", "errors", "details");
        }
    }

    @Test
    void CSRF_헤더가_없으면_거부하고_글은_그대로() throws Exception {
        long postId = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);

        MvcResult result = api.changeWithoutCsrf(session, postId, "PRIVATE");

        assertThat(status(result)).isEqualTo(403);
        assertThat((String) read(result, "$.code")).isEqualTo("CSRF_REJECTED");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT visibility FROM post WHERE id = ?", String.class, postId))
                .isEqualTo("PUBLIC");
    }

    @Test
    void 비회원_401은_LoginRequired_본문() throws Exception {
        long postId = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);

        MvcResult result = api.change(null, postId, "PRIVATE");

        assertThat(status(result)).isEqualTo(401);
        assertThat(body(result)).isEqualTo(LOGIN_REQUIRED);
    }

    @Test
    void 계정_상태_403은_AccountStateDenied_형식() throws Exception {
        long unverified = members().member().emailVerified(false).create();
        long unverifiedPost = posts.create(unverified, PostFixtures.State.PUBLISHED_PUBLIC);
        MvcResult emailNotVerified =
                api.change(TestLogin.loginAs(mockMvc, unverified), unverifiedPost, "PRIVATE");
        assertThat(status(emailNotVerified)).isEqualTo(403);
        assertThat(body(emailNotVerified))
                .isEqualTo(
                        "{\"code\":\"EMAIL_NOT_VERIFIED\",\"message\":\"이메일 인증 후 이용할 수 있어요\","
                                + "\"errors\":[],\"details\":{\"action\":\"RESEND_VERIFICATION\"}}");

        long withdrawn = members().member().create();
        long withdrawnPost = posts.create(withdrawn, PostFixtures.State.PUBLISHED_PUBLIC);
        Cookie withdrawnSession = TestLogin.loginAs(mockMvc, withdrawn);
        posts.withdraw(withdrawn);
        MvcResult accountWithdrawn = api.change(withdrawnSession, withdrawnPost, "PRIVATE");
        assertThat(status(accountWithdrawn)).isEqualTo(403);
        assertThat(body(accountWithdrawn))
                .isEqualTo(
                        "{\"code\":\"ACCOUNT_WITHDRAWN\",\"message\":\"탈퇴 신청한 계정이에요\","
                                + "\"errors\":[],\"details\":{\"action\":\"RESTORE\"}}");

        long suspended = members().member().create();
        long suspendedPost = posts.create(suspended, PostFixtures.State.PUBLISHED_PUBLIC);
        Cookie suspendedSession = TestLogin.loginAs(mockMvc, suspended);
        members().suspend(suspended, Instant.now().plusSeconds(86_400), "계약 테스트");
        MvcResult accountSuspended = api.change(suspendedSession, suspendedPost, "PRIVATE");
        assertThat(status(accountSuspended)).isEqualTo(403);
        assertThat(body(accountSuspended))
                .isEqualTo(
                        "{\"code\":\"ACCOUNT_SUSPENDED\",\"message\":\"정지된 계정이에요\","
                                + "\"errors\":[],\"details\":null}");
    }

    @Test
    void 없는_글_404는_NotFound_본문과_no_store_숫자가_아니거나_1_미만인_번호도_같다() throws Exception {
        for (Object postId : new Object[] {posts.nonexistentId(), 0, -1, "abc"}) {
            MvcResult result = api.change(session, postId, "PRIVATE");
            assertThat(status(result)).as(String.valueOf(postId)).isEqualTo(404);
            assertThat(body(result)).as(String.valueOf(postId)).isEqualTo(NOT_FOUND);
            assertThat(cacheControl(result)).isEqualTo("private, no-store");
        }
    }

    @Test
    void PATCH는_지원하지_않는다() throws Exception {
        long postId = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);

        MvcResult result =
                mockMvc.perform(
                                TestLogin.withCsrf(
                                                org.springframework.test.web.servlet.request
                                                        .MockMvcRequestBuilders.patch(
                                                        VisibilityApi.PATH, postId),
                                                session)
                                        .contentType("application/json")
                                        .content("{\"visibility\":\"PRIVATE\"}"))
                        .andReturn();

        assertThat(status(result)).isEqualTo(405);
    }
}
