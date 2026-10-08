package com.team.blog.post.integration;

import static com.team.blog.post.support.EditorApi.body;
import static com.team.blog.post.support.EditorApi.publishBody;
import static com.team.blog.post.support.EditorApi.read;
import static com.team.blog.post.support.EditorApi.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import com.team.blog.post.support.AuthoringFixtures;
import com.team.blog.post.support.EditorApi;
import com.team.blog.post.support.PostTestConfig;
import com.team.blog.post.support.PostTestConfig.CommittedEvents;
import com.team.blog.shared.event.PostPublished;
import com.team.blog.shared.event.PostWentPublic;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import com.team.blog.tag.application.TagService;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;

/**
 * [발행]을 여러 번 눌러도 한 번만 발행된다 (002 T106, US6 #2·#3, SC-002, FR-037, research A-8·B-8). 같은 {@code
 * Idempotency-Key}는 처리 중이면 409 {@code IN_PROGRESS}, 끝났으면 저장된 응답, 다른 내용이면 422.
 */
@Import(PostTestConfig.class)
class PublishIdempotencyIT extends IntegrationTestBase {

    @Autowired CommittedEvents events;

    @MockitoSpyBean TagService tagService;

    private EditorApi api() {
        return new EditorApi(mockMvc);
    }

    private AuthoringFixtures fixtures() {
        return new AuthoringFixtures(jdbc, redis);
    }

    @BeforeEach
    void clearEvents() {
        events.clear();
    }

    @AfterEach
    void resetSpy() {
        reset(tagService);
    }

    private long version(long postId) {
        return jdbc.queryForObject(
                "SELECT edit_version FROM post WHERE id = ?", Long.class, postId);
    }

    private static String idemKey(long memberId, String key) {
        return "idem:publish:" + memberId + ":" + key;
    }

    @Test
    void 같은_키_같은_내용_동시_20건이면_한_번만_발행된다() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = api().createPostId(session);
        String key = UUID.randomUUID().toString();
        Map<String, Object> request = publishBody("연타", "본문", List.of("spring"), "PUBLIC", 0);
        int n = 20;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<MvcResult>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < n; i++) {
                futures.add(
                        pool.submit(
                                () -> {
                                    ready.countDown();
                                    go.await();
                                    return api().publish(session, postId, request, key);
                                }));
            }
            ready.await(10, TimeUnit.SECONDS);
            go.countDown();
            List<MvcResult> results = new ArrayList<>();
            for (Future<MvcResult> f : futures) {
                results.add(f.get(30, TimeUnit.SECONDS));
            }

            assertThat(version(postId)).isEqualTo(1);
            List<MvcResult> ok = results.stream().filter(r -> status(r) == 200).toList();
            List<MvcResult> others = results.stream().filter(r -> status(r) != 200).toList();
            assertThat(ok).isNotEmpty();
            String first = body(ok.get(0));
            assertThat(ok).allSatisfy(r -> assertThat(body(r)).isEqualTo(first));
            assertThat(others)
                    .allSatisfy(
                            r -> {
                                assertThat(status(r)).as(body(r)).isEqualTo(409);
                                assertThat((String) read(r, "$.code")).isEqualTo("IN_PROGRESS");
                            });
            assertThat(events.of(PostPublished.class)).hasSize(1);
            assertThat(events.of(PostWentPublic.class)).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void 끝난_키를_다시_보내면_저장된_응답을_돌려주고_이벤트는_다시_내지_않는다() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = api().createPostId(session);
        String key = UUID.randomUUID().toString();
        Map<String, Object> request = publishBody("한 번", "본문", List.of(), "PUBLIC", 0);

        MvcResult first = api().publish(session, postId, request, key);
        MvcResult again = api().publish(session, postId, request, key);

        assertThat(status(first)).as(body(first)).isEqualTo(200);
        assertThat(status(again)).as(body(again)).isEqualTo(200);
        assertThat(body(again)).isEqualTo(body(first));
        assertThat(version(postId)).isEqualTo(1);
        assertThat(events.of(PostPublished.class)).hasSize(1);

        String stored = redis.opsForValue().get(idemKey(me, key));
        assertThat(stored).isNotNull();
        assertThat((String) JsonPath.read(stored, "$.status")).isEqualTo("DONE");
        assertThat((String) JsonPath.read(stored, "$.hash")).matches("[0-9a-f]{64}");
        assertThat((String) JsonPath.read(stored, "$.response.url"))
                .isEqualTo(read(first, "$.url"));
        Long ttl = redis.getExpire(idemKey(me, key), TimeUnit.SECONDS);
        assertThat(ttl).isBetween(590L, 600L);
    }

    @Test
    void 같은_키로_다른_내용을_보내면_422() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = api().createPostId(session);
        String key = UUID.randomUUID().toString();
        assertThat(
                        status(
                                api().publish(
                                                session,
                                                postId,
                                                publishBody("처음", "본문", List.of(), "PUBLIC", 0),
                                                key)))
                .isEqualTo(200);

        MvcResult reused =
                api().publish(
                                session,
                                postId,
                                publishBody("다른 제목", "본문", List.of(), "PUBLIC", 1),
                                key);

        assertThat(status(reused)).isEqualTo(422);
        assertThat((String) read(reused, "$.code")).isEqualTo("IDEMPOTENCY_KEY_REUSED");
        assertThat((String) read(reused, "$.message")).isEqualTo("같은 요청 키로 다른 내용을 보낼 수 없어요");
        assertThat(version(postId)).isEqualTo(1);
    }

    @Test
    void 같은_키를_다른_글에_쓰면_422() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long first = api().createPostId(session);
        long second = api().createPostId(session);
        String key = UUID.randomUUID().toString();
        Map<String, Object> request = publishBody("같은 내용", "본문", List.of(), "PUBLIC", 0);
        assertThat(status(api().publish(session, first, request, key))).isEqualTo(200);

        MvcResult other = api().publish(session, second, request, key);

        assertThat(status(other)).isEqualTo(422);
        assertThat((String) read(other, "$.code")).isEqualTo("IDEMPOTENCY_KEY_REUSED");
        assertThat(version(second)).isZero();
    }

    @Test
    void 검증_실패_뒤에는_키가_풀려_같은_키로_다시_판정한다() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = api().createPostId(session);
        String key = UUID.randomUUID().toString();
        Map<String, Object> invalid = publishBody("", "본문", List.of(), "PUBLIC", 0);

        MvcResult first = api().publish(session, postId, invalid, key);
        MvcResult again = api().publish(session, postId, invalid, key);

        assertThat(status(first)).isEqualTo(400);
        assertThat(status(again)).as(body(again)).isEqualTo(400);
        assertThat((String) read(again, "$.code")).isEqualTo("VALIDATION_FAILED");
        assertThat(redis.hasKey(idemKey(me, key))).isFalse();
    }

    @Test
    void 버전_충돌_뒤에도_키가_풀린다() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = api().createPostId(session);
        String key = UUID.randomUUID().toString();

        MvcResult conflict =
                api().publish(session, postId, publishBody("t", "b", List.of(), "PUBLIC", 9), key);

        assertThat(status(conflict)).isEqualTo(409);
        assertThat((String) read(conflict, "$.code")).isEqualTo("VERSION_CONFLICT");
        assertThat(redis.hasKey(idemKey(me, key))).isFalse();
    }

    @Test
    void 트랜잭션이_실패하면_키가_풀려_같은_키_같은_내용으로_다시_발행할_수_있다() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = api().createPostId(session);
        String key = UUID.randomUUID().toString();
        Map<String, Object> request = publishBody("재시도", "본문", List.of("spring"), "PUBLIC", 0);
        doThrow(new IllegalStateException("태그 확정 실패"))
                .when(tagService)
                .replacePostTags(anyLong(), anyList());

        MvcResult failed = api().publish(session, postId, request, key);
        reset(tagService);
        MvcResult retried = api().publish(session, postId, request, key);

        assertThat(status(failed)).isEqualTo(500);
        assertThat(status(retried)).as(body(retried)).isEqualTo(200);
        assertThat(version(postId)).isEqualTo(1);
        assertThat(events.of(PostPublished.class)).hasSize(1);
    }

    @Test
    void JSON_키_순서와_공백만_다른_같은_요청은_같은_요청으로_본다() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = api().createPostId(session);
        String key = UUID.randomUUID().toString();
        String a =
                "{\"title\":\"순서\",\"contentMd\":\"본문\",\"tags\":[\"a\",\"b\"],"
                        + "\"visibility\":\"PUBLIC\",\"baseVersion\":0}";
        String b =
                "{ \"baseVersion\" : 0 ,\n \"visibility\":\"PUBLIC\", \"tags\" : [ \"a\", \"b\" ],"
                        + " \"contentMd\":\"본문\",  \"title\":\"순서\" }";

        MvcResult first = raw(session, postId, key, a);
        MvcResult second = raw(session, postId, key, b);

        assertThat(status(first)).as(body(first)).isEqualTo(200);
        assertThat(status(second)).as(body(second)).isEqualTo(200);
        assertThat(body(second)).isEqualTo(body(first));
        assertThat(version(postId)).isEqualTo(1);
    }

    @Test
    void 다른_키로_다시_발행하면_기준_버전으로_판정한다() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = fixtures().posts().post(me).title("t").contentMd("b").create();
        Map<String, Object> request = publishBody("t", "b", List.of(), "PUBLIC", 0);

        assertThat(status(api().publish(session, postId, request))).isEqualTo(200);
        MvcResult second = api().publish(session, postId, request);

        assertThat(status(second)).isEqualTo(409);
        assertThat((String) read(second, "$.code")).isEqualTo("VERSION_CONFLICT");
    }

    private MvcResult raw(Cookie session, long postId, String key, String json) throws Exception {
        return mockMvc.perform(
                        TestLogin.withCsrf(post("/api/posts/{postId}/publish", postId), session)
                                .header("Idempotency-Key", key)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json))
                .andReturn();
    }
}
