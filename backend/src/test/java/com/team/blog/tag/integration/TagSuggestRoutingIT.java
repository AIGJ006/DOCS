package com.team.blog.tag.integration;

import static com.team.blog.tag.support.TagApi.body;
import static com.team.blog.tag.support.TagApi.read;
import static com.team.blog.tag.support.TagApi.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.post.application.OwnedPost;
import com.team.blog.post.domain.Visibility;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import com.team.blog.support.ai.FakeAiConfiguration.FakeAi;
import com.team.blog.support.fixture.PostFixtures;
import com.team.blog.tag.application.suggest.FailureKind;
import com.team.blog.tag.application.suggest.ProviderState;
import com.team.blog.tag.application.suggest.QuotaKind;
import com.team.blog.tag.application.suggest.SuggestOutcome;
import com.team.blog.tag.application.suggest.TagSuggesterRouter;
import com.team.blog.tag.support.AiSuggestApi;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 공급자 전환 (013 T044, US4 #1~#5, SC-005, contracts/providers.md §5). 시간을 바꾸는 대신 Redis 상태 키를 직접 두고
 * 지운다(예: 60초 쉬기 끝 = {@code ai:gemini:cooldown} 삭제).
 */
class TagSuggestRoutingIT extends IntegrationTestBase {

    private static final ZoneId PACIFIC = ZoneId.of("America/Los_Angeles");
    private static final String EXHAUSTED = "ai:gemini:exhausted";
    private static final String COOLDOWN = "ai:gemini:cooldown";
    private static final String INFLIGHT = "ai:ollama:inflight";

    @Autowired private FakeAi fakeAi;
    @Autowired private ProviderState providerState;
    @Autowired private TagSuggesterRouter router;

    private AiSuggestApi api;

    @BeforeEach
    void setUp() {
        fakeAi.reset();
        api = new AiSuggestApi(mockMvc, jdbc);
    }

    private record Author(long id, long postId, Cookie session) {}

    private Author author(PostFixtures.State state) {
        long id = members().member().create();
        api.consent(id);
        long post = new PostFixtures(jdbc).create(id, state);
        return new Author(id, post, TestLogin.loginAs(mockMvc, id));
    }

    private Author author() {
        return author(PostFixtures.State.PUBLISHED_PUBLIC);
    }

    /** 재사용에 걸리지 않도록 요청마다 다른 내용 + [다시 추천]. */
    private static Map<String, Object> fresh(String suffix) {
        return AiSuggestApi.body(
                "JPA N+1 정리", AiSuggestApi.LONG_BODY + "\n\n" + suffix, List.of(), true);
    }

    private MvcResult suggest(Author a, String suffix) throws Exception {
        return api.suggest(a.session(), a.postId(), fresh(suffix));
    }

    private String providerOf(Author a) throws Exception {
        MvcResult r = api.status(a.session(), a.postId());
        assertThat(status(r)).isEqualTo(200);
        return read(r, "$.provider");
    }

    private static String pacificDate(Instant now) {
        return LocalDate.ofInstant(now, PACIFIC).format(DateTimeFormatter.BASIC_ISO_DATE);
    }

    private static String usageKey(long memberId) {
        return "ai:tag:usage:"
                + memberId
                + ":"
                + LocalDate.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.BASIC_ISO_DATE);
    }

    private static long secondsToPacificMidnight() {
        ZonedDateTime now = ZonedDateTime.now(PACIFIC);
        return Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay(PACIFIC))
                .toSeconds();
    }

    private void assertExhaustedUntilPacificMidnight() {
        Long ttl = redis.getExpire(EXHAUSTED);
        assertThat(ttl).isNotNull();
        assertThat(ttl).isBetween(secondsToPacificMidnight() - 30, secondsToPacificMidnight() + 1);
    }

    // ---- US4 #1 우리 집계 ----

    @Test
    void 우리_집계가_450이면_자체_AI로_가고_소진을_적는다() throws Exception {
        Author a = author();
        assertThat(providerOf(a)).isEqualTo("GEMINI");
        redis.opsForValue().set("ai:gemini:count:" + pacificDate(Instant.now()), "450");

        assertThat(providerOf(a)).isEqualTo("OLLAMA");
        MvcResult r = suggest(a, "하나");
        assertThat(status(r)).as(body(r)).isEqualTo(200);
        assertThat((String) read(r, "$.provider")).isEqualTo("OLLAMA");
        assertThat(fakeAi.gemini().calls()).isZero();
        assertThat(fakeAi.ollama().calls()).isEqualTo(1);
        assertExhaustedUntilPacificMidnight();
    }

    @Test
    void Gemini_호출은_공급자_날짜_키로_센다() throws Exception {
        Author a = author();
        assertThat(status(suggest(a, "하나"))).isEqualTo(200);
        String key = "ai:gemini:count:" + pacificDate(Instant.now());
        assertThat(redis.opsForValue().get(key)).isEqualTo("1");
        assertThat(redis.getExpire(key))
                .isBetween(Duration.ofDays(2).toSeconds() - 60, Duration.ofDays(2).toSeconds());
    }

    @Test
    void 공급자_날짜_경계는_태평양_시간을_따른다() {
        // 한국 2026-10-09 10:00 = 태평양 2026-10-08 18:00 — 한국 날짜 키가 아니라 태평양 날짜 키를 본다
        Instant now = Instant.parse("2026-10-09T01:00:00Z");
        OwnedPost post = new OwnedPost(1L, Visibility.PUBLIC);
        redis.opsForValue().set("ai:gemini:count:20261009", "450");
        assertThat(router.choose(post, now)).isEqualTo(TagSuggesterRouter.Route.GEMINI);
        redis.opsForValue().set("ai:gemini:count:20261008", "450");
        assertThat(router.choose(post, now)).isEqualTo(TagSuggesterRouter.Route.OLLAMA);
        // 소진 TTL = 태평양 다음 0시(2026-10-09T07:00Z)까지
        Long ttl = redis.getExpire(EXHAUSTED);
        assertThat(ttl)
                .isBetween(Duration.ofHours(6).toSeconds() - 5, Duration.ofHours(6).toSeconds());
    }

    // ---- US4 #2 하루 한도 429 ----

    @Test
    void PER_DAY_429는_같은_요청을_자체_AI로_200() throws Exception {
        Author a = author();
        fakeAi.gemini().respond(new SuggestOutcome.QuotaExceeded(QuotaKind.PER_DAY));
        MvcResult r = suggest(a, "하나");
        assertThat(status(r)).as(body(r)).isEqualTo(200);
        assertThat((String) read(r, "$.provider")).isEqualTo("OLLAMA");
        assertThat((Integer) read(r, "$.remainingToday")).isEqualTo(19);
        assertThat(fakeAi.gemini().calls()).isEqualTo(1);
        assertThat(fakeAi.ollama().calls()).isEqualTo(1);
        assertExhaustedUntilPacificMidnight();
        assertThat(providerOf(a)).isEqualTo("OLLAMA");

        assertThat((String) read(suggest(a, "둘"), "$.provider")).isEqualTo("OLLAMA");
        assertThat(fakeAi.gemini().calls()).isEqualTo(1);
    }

    // ---- US4 #3 분당 한도 ----

    @Test
    void PER_MINUTE_429는_60초_쉬고_다시_Gemini() throws Exception {
        Author a = author();
        fakeAi.gemini().respond(new SuggestOutcome.QuotaExceeded(QuotaKind.PER_MINUTE));
        MvcResult r = suggest(a, "하나");
        assertThat(status(r)).as(body(r)).isEqualTo(200);
        assertThat((String) read(r, "$.provider")).isEqualTo("OLLAMA");
        assertThat(redis.getExpire(COOLDOWN)).isBetween(55L, 60L);
        assertThat(redis.hasKey(EXHAUSTED)).isFalse();

        assertThat(providerOf(a)).isEqualTo("OLLAMA");
        assertThat((String) read(suggest(a, "둘"), "$.provider")).isEqualTo("OLLAMA");

        redis.delete(COOLDOWN); // 60초가 지남
        assertThat(providerOf(a)).isEqualTo("GEMINI");
        assertThat((String) read(suggest(a, "셋"), "$.provider")).isEqualTo("GEMINI");
        assertThat(fakeAi.gemini().calls()).isEqualTo(2);
    }

    @Test
    void 종류_모르는_429_세_번이면_소진() throws Exception {
        Author a = author();
        for (int i = 1; i <= 3; i++) {
            redis.delete(COOLDOWN);
            fakeAi.gemini().respond(new SuggestOutcome.QuotaExceeded(QuotaKind.UNKNOWN));
            MvcResult r = suggest(a, "번째 " + i);
            assertThat(status(r)).as(body(r)).isEqualTo(200);
            assertThat((String) read(r, "$.provider")).isEqualTo("OLLAMA");
            assertThat(redis.hasKey(EXHAUSTED)).as("%d번째", i).isEqualTo(i == 3);
        }
        assertThat(fakeAi.gemini().calls()).isEqualTo(3);
        assertExhaustedUntilPacificMidnight();
    }

    // ---- US4 #4 시간 초과 ----

    @Test
    void 시간_초과는_이번_요청_503_FAILED_횟수_되돌림_60초_자체_AI() throws Exception {
        Author a = author();
        fakeAi.gemini().respond(new SuggestOutcome.Failed(FailureKind.TIMEOUT));
        MvcResult r = suggest(a, "하나");
        assertThat(status(r)).as(body(r)).isEqualTo(503);
        assertThat((String) read(r, "$.code")).isEqualTo("AI_UNAVAILABLE");
        assertThat((String) read(r, "$.details.reason")).isEqualTo("FAILED");
        assertThat(fakeAi.ollama().calls()).isZero();
        String used = redis.opsForValue().get(usageKey(a.id()));
        assertThat(used == null || "0".equals(used)).as(used).isTrue();
        assertThat(redis.getExpire(COOLDOWN)).isBetween(55L, 60L);

        MvcResult next = suggest(a, "둘");
        assertThat(status(next)).isEqualTo(200);
        assertThat((String) read(next, "$.provider")).isEqualTo("OLLAMA");
        assertThat((Integer) read(next, "$.remainingToday")).isEqualTo(19);
    }

    // ---- 비공개 글 ----

    @Test
    void 비공개_글은_자체_AI만_쓰고_꺼져_있으면_503_FAILED() throws Exception {
        Author a = author(PostFixtures.State.PUBLISHED_PRIVATE);
        assertThat(providerOf(a)).isEqualTo("OLLAMA");
        MvcResult ok = suggest(a, "하나");
        assertThat(status(ok)).as(body(ok)).isEqualTo(200);
        assertThat((String) read(ok, "$.provider")).isEqualTo("OLLAMA");
        assertThat(fakeAi.gemini().calls()).isZero();

        fakeAi.ollama().configured(false);
        assertThat((String) read(api.status(a.session(), a.postId()), "$.provider")).isNull();
        MvcResult r = suggest(a, "둘");
        assertThat(status(r)).as(body(r)).isEqualTo(503);
        assertThat((String) read(r, "$.details.reason")).isEqualTo("FAILED");
        assertThat(fakeAi.totalCalls()).isEqualTo(1);
        assertThat((Integer) read(api.status(a.session(), a.postId()), "$.remainingToday"))
                .isEqualTo(19);
    }

    @Test
    void 키가_없으면_공개_글도_자체_AI() throws Exception {
        Author a = author();
        fakeAi.gemini().configured(false);
        assertThat(providerOf(a)).isEqualTo("OLLAMA");
        assertThat((String) read(suggest(a, "하나"), "$.provider")).isEqualTo("OLLAMA");
        assertThat(fakeAi.gemini().calls()).isZero();
    }

    // ---- US4 #5 자체 AI 동시 처리 1 ----

    @Test
    void 자체_AI_동시_두_번째는_503_BUSY_횟수_되돌림() throws Exception {
        fakeAi.gemini().configured(false);
        Author a = author();
        Author b = author();
        fakeAi.ollama().hold();
        CompletableFuture<MvcResult> first =
                CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                return suggest(a, "하나");
                            } catch (Exception e) {
                                throw new IllegalStateException(e);
                            }
                        });
        try {
            assertThat(fakeAi.ollama().awaitEntered()).isTrue();
            assertThat(redis.opsForValue().get(INFLIGHT)).isEqualTo("1");
            assertThat(redis.getExpire(INFLIGHT)).isBetween(1L, 60L);

            MvcResult second = suggest(b, "둘");
            assertThat(status(second)).as(body(second)).isEqualTo(503);
            assertThat((String) read(second, "$.details.reason")).isEqualTo("BUSY");
            assertThat((String) read(second, "$.message")).isEqualTo("잠시 후 다시 시도해 주세요");
            String used = redis.opsForValue().get(usageKey(b.id()));
            assertThat(used == null || "0".equals(used)).as(used).isTrue();
        } finally {
            fakeAi.ollama().release();
        }
        MvcResult done = first.get(15, TimeUnit.SECONDS);
        assertThat(status(done)).as(body(done)).isEqualTo(200);
        assertThat(redis.opsForValue().get(INFLIGHT)).isEqualTo("0");
        assertThat(fakeAi.ollama().calls()).isEqualTo(1);
    }

    @Test
    void 상태_API_예측은_소진과_쉬기를_따른다() throws Exception {
        Author a = author();
        assertThat(providerOf(a)).isEqualTo("GEMINI");
        providerState.cooldown();
        assertThat(providerOf(a)).isEqualTo("OLLAMA");
        redis.delete(COOLDOWN);
        providerState.exhaust(Instant.now());
        assertThat(providerOf(a)).isEqualTo("OLLAMA");
        redis.delete(EXHAUSTED);
        assertThat(providerOf(a)).isEqualTo("GEMINI");
    }
}
