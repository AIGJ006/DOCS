package com.team.blog.tag.integration;

import static com.team.blog.post.support.EditorApi.publishBody;
import static com.team.blog.post.support.EditorApi.saveBody;
import static com.team.blog.tag.support.TagApi.body;
import static com.team.blog.tag.support.TagApi.read;
import static com.team.blog.tag.support.TagApi.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.team.blog.post.support.EditorApi;
import com.team.blog.shared.infra.redis.RedisGuard;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import com.team.blog.support.ai.FakeAiConfiguration.FakeAi;
import com.team.blog.support.fixture.PostFixtures;
import com.team.blog.tag.application.suggest.AiUnavailableException;
import com.team.blog.tag.application.suggest.AiUnavailableReason;
import com.team.blog.tag.application.suggest.DailyUsage;
import com.team.blog.tag.application.suggest.FailureKind;
import com.team.blog.tag.application.suggest.Provider;
import com.team.blog.tag.application.suggest.ProviderState;
import com.team.blog.tag.application.suggest.SuggestCache;
import com.team.blog.tag.application.suggest.SuggestInputCleaner;
import com.team.blog.tag.application.suggest.SuggestOutcome;
import com.team.blog.tag.application.suggest.TagSuggestRequest;
import com.team.blog.tag.application.suggest.TagSuggestService;
import com.team.blog.tag.support.AiSuggestApi;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 장애 격리 (013 T051, SC-002, FR-019). 추천이 실패해도 002 자동 저장·저장·발행은 그대로 된다.
 *
 * <ul>
 *   <li>Redis 장애는 002 시험처럼 회로 차단기를 강제로 열어 모사한다 — 세션 저장소도 Redis라 컨테이너를 멈추면 로그인 자체가 끊긴다.
 *   <li>메모리 부족은 공유 컨테이너에 {@code CONFIG SET maxmemory 1}을 잠깐 걸고(쓰기만 거부) 서비스·구성 요소를 직접 부른다 — MockMvc
 *       요청은 세션 쓰기에서 막힌다. 끝나면 되돌린다.
 * </ul>
 */
class TagSuggestFailureIsolationIT extends IntegrationTestBase {

    @Autowired private FakeAi fakeAi;
    @Autowired private CircuitBreakerRegistry circuitBreakers;
    @Autowired private DailyUsage usage;
    @Autowired private ProviderState providerState;
    @Autowired private SuggestCache cache;
    @Autowired private SuggestInputCleaner cleaner;

    @Autowired
    @Qualifier("aiTagSuggestService")
    private TagSuggestService service;

    private AiSuggestApi api;

    @BeforeEach
    void setUp() {
        fakeAi.reset();
        api = new AiSuggestApi(mockMvc, jdbc);
    }

    @AfterEach
    void recover() throws Exception {
        circuitBreakers.circuitBreaker(RedisGuard.CIRCUIT_BREAKER).reset();
        redisCli("CONFIG", "SET", "maxmemory", "0");
    }

    private static void redisCli(String... args) throws Exception {
        String[] command = new String[args.length + 1];
        command[0] = "redis-cli";
        System.arraycopy(args, 0, command, 1, args.length);
        var result = IntegrationTestBase.redisContainer().execInContainer(command);
        assertThat(result.getExitCode()).as(result.getStderr()).isZero();
    }

    private void redisDown() {
        circuitBreakers.circuitBreaker(RedisGuard.CIRCUIT_BREAKER).transitionToForcedOpenState();
    }

    private void bothProvidersFail() {
        fakeAi.gemini().respond(new SuggestOutcome.Failed(FailureKind.SERVER_ERROR));
        fakeAi.ollama().respond(new SuggestOutcome.Failed(FailureKind.TIMEOUT));
    }

    private static long version(MvcResult r) {
        return ((Number) read(r, "$.version")).longValue();
    }

    @Test
    void 두_공급자_실패에도_자동_저장_저장_발행은_된다() throws Exception {
        long me = members().member().create();
        api.consent(me);
        Cookie session = TestLogin.loginAs(mockMvc, me);
        EditorApi editor = new EditorApi(mockMvc);
        long postId = editor.createPostId(session);
        bothProvidersFail();

        MvcResult suggest = api.suggest(session, postId, AiSuggestApi.body(List.of()));
        assertThat(status(suggest)).as(body(suggest)).isEqualTo(503);
        assertThat((String) read(suggest, "$.details.reason")).isEqualTo("FAILED");

        MvcResult autosave =
                editor.autosave(session, postId, saveBody("제목", AiSuggestApi.LONG_BODY, 0));
        assertThat(status(autosave)).as(body(autosave)).isEqualTo(200);
        MvcResult save =
                editor.save(
                        session, postId, saveBody("제목", AiSuggestApi.LONG_BODY, version(autosave)));
        assertThat(status(save)).as(body(save)).isEqualTo(200);
        MvcResult publish =
                editor.publish(
                        session,
                        postId,
                        publishBody(
                                "제목",
                                AiSuggestApi.LONG_BODY,
                                List.of("jpa"),
                                "PUBLIC",
                                version(save)));
        assertThat(status(publish)).as(body(publish)).isEqualTo(200);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM post WHERE id = ?", String.class, postId))
                .isEqualTo("PUBLISHED");
    }

    @Test
    void Redis_장애면_추천은_503_STORE_UNAVAILABLE이고_글쓰기는_된다() throws Exception {
        long me = members().member().create();
        api.consent(me);
        Cookie session = TestLogin.loginAs(mockMvc, me);
        EditorApi editor = new EditorApi(mockMvc);
        long postId = editor.createPostId(session);
        bothProvidersFail();
        redisDown();

        MvcResult suggest = api.suggest(session, postId, AiSuggestApi.body(List.of()));
        assertThat(status(suggest)).as(body(suggest)).isEqualTo(503);
        assertThat((String) read(suggest, "$.code")).isEqualTo("AI_UNAVAILABLE");
        assertThat((String) read(suggest, "$.details.reason")).isEqualTo("STORE_UNAVAILABLE");
        assertThat(fakeAi.totalCalls()).isZero();

        MvcResult st = api.status(session, postId);
        assertThat(status(st)).isEqualTo(200);
        assertThat((Boolean) read(st, "$.available")).isFalse();

        MvcResult autosave =
                editor.autosave(session, postId, saveBody("장애 중", AiSuggestApi.LONG_BODY, 0));
        assertThat(status(autosave)).as(body(autosave)).isEqualTo(200);
        MvcResult save =
                editor.save(
                        session,
                        postId,
                        saveBody("장애 중", AiSuggestApi.LONG_BODY, version(autosave)));
        assertThat(status(save)).as(body(save)).isEqualTo(200);
        MvcResult publish =
                editor.publish(
                        session,
                        postId,
                        publishBody(
                                "장애 중",
                                AiSuggestApi.LONG_BODY,
                                List.of(),
                                "PUBLIC",
                                version(save)));
        assertThat(status(publish)).as(body(publish)).isEqualTo(200);
    }

    @Test
    void Redis_메모리_부족도_STORE_UNAVAILABLE로_바뀐다() throws Exception {
        long me = members().member().create();
        api.consent(me);
        long postId = new PostFixtures(jdbc).create(me, PostFixtures.State.PUBLISHED_PUBLIC);
        Instant now = Instant.now();
        var input = cleaner.clean("JPA N+1 정리", AiSuggestApi.LONG_BODY);
        TagSuggestRequest request =
                new TagSuggestRequest("JPA N+1 정리", AiSuggestApi.LONG_BODY, List.of(), false);

        redisCli("CONFIG", "SET", "maxmemory", "1");
        try {
            // 판단에 쓰는 쓰기 — 503 STORE_UNAVAILABLE (002 자동 저장 문구가 아니라)
            assertThat(
                            catchThrowableOfType(
                                            AiUnavailableException.class,
                                            () ->
                                                    service.suggest(
                                                            me, String.valueOf(postId), request))
                                    .reason())
                    .isEqualTo(AiUnavailableReason.STORE_UNAVAILABLE);
            assertThat(
                            catchThrowableOfType(
                                            AiUnavailableException.class,
                                            () -> usage.reserve(me, now))
                                    .reason())
                    .isEqualTo(AiUnavailableReason.STORE_UNAVAILABLE);
            assertThat(
                            catchThrowableOfType(
                                            AiUnavailableException.class,
                                            providerState::acquireOllama)
                                    .reason())
                    .isEqualTo(AiUnavailableReason.STORE_UNAVAILABLE);
            // 호출 뒤 기록·되돌리기·저장은 응답을 바꾸지 않는다
            assertThatCode(
                            () -> {
                                cache.store(postId, input, List.of("jpa"), Provider.GEMINI, now);
                                providerState.cooldown();
                                providerState.exhaust(now);
                                providerState.countGeminiCall(now);
                                providerState.releaseOllama();
                                usage.release(me, now);
                            })
                    .doesNotThrowAnyException();
            // 읽기는 된다
            assertThat(cache.lookup(postId, input, false, () -> true)).isEmpty();
            assertThat(service.status(me, String.valueOf(postId)).available()).isTrue();
        } finally {
            redisCli("CONFIG", "SET", "maxmemory", "0");
        }
        assertThat(fakeAi.totalCalls()).isZero();
        assertThat(redis.keys("ai:*")).isEmpty();
    }
}
