package com.team.blog.tag.integration;

import static com.team.blog.tag.support.TagApi.body;
import static com.team.blog.tag.support.TagApi.read;
import static com.team.blog.tag.support.TagApi.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import com.team.blog.support.ai.FakeAiConfiguration.FakeAi;
import com.team.blog.support.fixture.PostFixtures;
import com.team.blog.tag.application.suggest.AiRedis;
import com.team.blog.tag.application.suggest.FailureKind;
import com.team.blog.tag.application.suggest.SuggestCache;
import com.team.blog.tag.application.suggest.SuggestInputCleaner;
import com.team.blog.tag.application.suggest.SuggestOutcome;
import com.team.blog.tag.application.suggest.TagSuggestProperties;
import com.team.blog.tag.support.AiSuggestApi;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

/**
 * 재사용 저장소 (013 T040, US3 #1~#4, SC-004, FR-026~FR-029, research R8). AI는 가짜 공급자. Redis는 시험마다 비운다.
 */
class TagSuggestCacheIT extends IntegrationTestBase {

    private static final String TITLE = "JPA N+1 정리";

    /** 문단 추가용 — 원문과 겹치는 3-gram이 거의 없는 긴 문단. */
    private static final String NEW_PARAGRAPH =
            "\n\n## 덧붙임\n\n"
                    + "쿠버네티스 파드 오토스케일링 설정값과 헬름 차트 배포 순서를 정리했다. 노드 풀 크기와 리소스 요청량,"
                    + " 프로메테우스 경보 규칙, 그라파나 대시보드 구성, 인그레스 컨트롤러 인증서 갱신 주기까지 함께 기록한다.";

    @Autowired private FakeAi fakeAi;
    @Autowired private SuggestCache cache;
    @Autowired private SuggestInputCleaner cleaner;
    @Autowired private TagSuggestProperties properties;
    @Autowired private AiRedis aiRedis;
    @Autowired private JsonMapper jsonMapper;

    private AiSuggestApi api;

    @BeforeEach
    void setUp() {
        fakeAi.reset();
        api = new AiSuggestApi(mockMvc, jdbc);
    }

    private record Author(long id, long postId, Cookie session) {}

    private Author author() {
        long id = members().member().create();
        api.consent(id);
        long post = new PostFixtures(jdbc).create(id, PostFixtures.State.PUBLISHED_PUBLIC);
        return new Author(id, post, TestLogin.loginAs(mockMvc, id));
    }

    private static Map<String, Object> req(String content, List<String> current, boolean refresh) {
        return AiSuggestApi.body(TITLE, content, current, refresh);
    }

    private static Map<String, Object> req(List<String> current) {
        return req(AiSuggestApi.LONG_BODY, current, false);
    }

    private static List<String> tags(MvcResult r) {
        return read(r, "$.tags");
    }

    private MvcResult ok(Author a, Map<String, Object> body) throws Exception {
        MvcResult r = api.suggest(a.session(), a.postId(), body);
        assertThat(status(r)).as(body(r)).isEqualTo(200);
        return r;
    }

    private String exactKey(String content) {
        return "ai:tag:v1:" + cleaner.clean(TITLE, content).sha256();
    }

    private Set<String> aiTagKeys() {
        return redis.keys("ai:tag:v*");
    }

    private String usageKey(long memberId) {
        return "ai:tag:usage:"
                + memberId
                + ":"
                + LocalDate.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.BASIC_ISO_DATE);
    }

    // ---- US3 #1·#2 ----

    @Test
    void 같은_입력_두_번째는_재사용_호출_0_횟수_그대로() throws Exception {
        Author a = author();
        MvcResult first = ok(a, req(List.of()));
        assertThat((Boolean) read(first, "$.cached")).isFalse();
        assertThat((Integer) read(first, "$.remainingToday")).isEqualTo(19);

        MvcResult second = ok(a, req(List.of()));
        assertThat((Boolean) read(second, "$.cached")).isTrue();
        assertThat((String) read(second, "$.provider")).isEqualTo("GEMINI");
        assertThat(tags(second)).isEqualTo(tags(first));
        assertThat((Integer) read(second, "$.remainingToday")).isEqualTo(19);
        assertThat(fakeAi.totalCalls()).isEqualTo(1);
        assertThat(redis.opsForValue().get(usageKey(a.id()))).isEqualTo("1");
    }

    @Test
    void 다른_회원도_같은_입력이면_재사용() throws Exception {
        Author a = author();
        Author b = author();
        ok(a, req(List.of()));
        MvcResult r = ok(b, req(List.of()));
        assertThat((Boolean) read(r, "$.cached")).isTrue();
        assertThat((Integer) read(r, "$.remainingToday")).isEqualTo(20);
        assertThat(fakeAi.totalCalls()).isEqualTo(1);
    }

    @Test
    void 태그를_붙인_뒤_재사용하면_그_태그는_빠진다() throws Exception {
        Author a = author();
        List<String> first = tags(ok(a, req(List.of())));
        assertThat(first).contains("spring");
        MvcResult r = ok(a, req(List.of("Spring")));
        assertThat((Boolean) read(r, "$.cached")).isTrue();
        assertThat(tags(r)).doesNotContain("spring").containsAll(first.subList(1, first.size()));
        assertThat(fakeAi.totalCalls()).isEqualTo(1);
    }

    // ---- US3 #3 같은 글 비슷한 내용 ----

    @Test
    void 오타_몇_개는_비슷한_내용으로_재사용() throws Exception {
        Author a = author();
        ok(a, req(List.of()));
        String typo = AiSuggestApi.LONG_BODY.replace("지연 로딩", "지연 로딍").replace("쿼리 수", "쿼리 슈");
        assertThat(typo).isNotEqualTo(AiSuggestApi.LONG_BODY);
        MvcResult r = ok(a, req(typo, List.of(), false));
        assertThat((Boolean) read(r, "$.cached")).isTrue();
        assertThat(fakeAi.totalCalls()).isEqualTo(1);
    }

    @Test
    void 비슷한_내용은_같은_글에서만() throws Exception {
        Author a = author();
        Author b = author();
        ok(a, req(List.of()));
        String typo = AiSuggestApi.LONG_BODY.replace("지연 로딩", "지연 로딍");
        MvcResult r = ok(b, req(typo, List.of(), false));
        assertThat((Boolean) read(r, "$.cached")).isFalse();
        assertThat(fakeAi.totalCalls()).isEqualTo(2);
    }

    @Test
    void 문단을_더하면_새로_부른다() throws Exception {
        Author a = author();
        ok(a, req(List.of()));
        MvcResult r = ok(a, req(AiSuggestApi.LONG_BODY + NEW_PARAGRAPH, List.of(), false));
        assertThat((Boolean) read(r, "$.cached")).isFalse();
        assertThat(fakeAi.totalCalls()).isEqualTo(2);
    }

    // ---- US3 #4 [다시 추천] ----

    @Test
    void refresh는_비슷한_내용을_건너뛰고_같은_내용은_쓴다() throws Exception {
        Author a = author();
        ok(a, req(List.of()));
        String typo = AiSuggestApi.LONG_BODY.replace("지연 로딩", "지연 로딍");
        MvcResult near = ok(a, req(typo, List.of(), true));
        assertThat((Boolean) read(near, "$.cached")).isFalse();
        assertThat(fakeAi.totalCalls()).isEqualTo(2);

        MvcResult exact = ok(a, req(AiSuggestApi.LONG_BODY, List.of(), true));
        assertThat((Boolean) read(exact, "$.cached")).isTrue();
        assertThat(fakeAi.totalCalls()).isEqualTo(2);
    }

    @Test
    void 같은_내용이_자체_AI_결과이고_지금_Gemini가_되면_refresh는_새로_만든다() throws Exception {
        Author a = author();
        fakeAi.gemini().configured(false);
        MvcResult first = ok(a, req(List.of()));
        assertThat((String) read(first, "$.provider")).isEqualTo("OLLAMA");
        assertThat(fakeAi.ollama().calls()).isEqualTo(1);

        fakeAi.gemini().configured(true);
        // refresh 없이는 자체 AI 결과를 그대로 쓴다
        MvcResult reuse = ok(a, req(List.of()));
        assertThat((Boolean) read(reuse, "$.cached")).isTrue();
        assertThat((String) read(reuse, "$.provider")).isEqualTo("OLLAMA");

        MvcResult fresh = ok(a, req(AiSuggestApi.LONG_BODY, List.of(), true));
        assertThat((Boolean) read(fresh, "$.cached")).isFalse();
        assertThat((String) read(fresh, "$.provider")).isEqualTo("GEMINI");
        assertThat(fakeAi.gemini().calls()).isEqualTo(1);
        // 새 결과로 덮어쓴다
        assertThat(redis.opsForValue().get(exactKey(AiSuggestApi.LONG_BODY))).contains("GEMINI");
    }

    // ---- FR-029 저장하지 않는 경우 ----

    @Test
    void 빈_결과는_저장하지_않는다() throws Exception {
        Author a = author();
        fakeAi.gemini().respondTags();
        assertThat(tags(ok(a, req(List.of())))).isEmpty();
        assertThat(aiTagKeys()).isEmpty();
        assertThat(redis.hasKey("ai:tag:post:" + a.postId())).isFalse();
    }

    @Test
    void 모두_걸러진_결과는_저장하지_않는다() throws Exception {
        Author a = author();
        fakeAi.gemini().respondTags("!!!", "###");
        assertThat(tags(ok(a, req(List.of())))).isEmpty();
        assertThat(aiTagKeys()).isEmpty();
    }

    @Test
    void 실패는_저장하지_않는다() throws Exception {
        Author a = author();
        fakeAi.gemini().respond(new SuggestOutcome.Failed(FailureKind.TIMEOUT));
        MvcResult r = api.suggest(a.session(), a.postId(), req(List.of()));
        assertThat(status(r)).isEqualTo(503);
        assertThat(aiTagKeys()).isEmpty();
        assertThat(redis.hasKey("ai:tag:post:" + a.postId())).isFalse();
    }

    // ---- 키·TTL·판 ----

    @Test
    void 키_이름과_TTL() throws Exception {
        Author a = author();
        ok(a, req(List.of()));
        String exact = exactKey(AiSuggestApi.LONG_BODY);
        assertThat(exact).matches("ai:tag:v1:[0-9a-f]{64}");
        assertThat(aiTagKeys()).containsExactly(exact);
        Long exactTtl = redis.getExpire(exact);
        Long postTtl = redis.getExpire("ai:tag:post:" + a.postId());
        assertThat(exactTtl)
                .isBetween(
                        Duration.ofDays(30).minusMinutes(1).toSeconds(),
                        Duration.ofDays(30).toSeconds());
        assertThat(postTtl)
                .isBetween(
                        Duration.ofDays(7).minusMinutes(1).toSeconds(),
                        Duration.ofDays(7).toSeconds());
    }

    @Test
    void prompt_version을_올리면_새_키라_재사용하지_않는다() throws Exception {
        Author a = author();
        ok(a, req(List.of()));
        TagSuggestProperties v2 =
                new TagSuggestProperties(
                        properties.enabled(),
                        2,
                        properties.minInputChars(),
                        properties.maxSuggestions(),
                        properties.dailyLimitPerMember(),
                        properties.cache(),
                        properties.popular(),
                        properties.gemini(),
                        properties.ollama());
        SuggestCache next = new SuggestCache(redis, aiRedis, jsonMapper, v2);
        var input = cleaner.clean(TITLE, AiSuggestApi.LONG_BODY);
        // 같은 내용 키는 v2로 바뀌고, 다른 글이라 비슷한 내용도 없다
        assertThat(next.lookup(a.postId() + 1_000_000, input, false, () -> true)).isEmpty();
        assertThat(cache.lookup(a.postId() + 1_000_000, input, false, () -> true)).isPresent();
    }

    // ---- FR-027 ----

    @Test
    void 하루_한도를_다_쓴_회원도_재사용_응답은_200() throws Exception {
        Author a = author();
        Author b = author();
        ok(a, req(List.of()));
        redis.opsForValue().set(usageKey(b.id()), "20", Duration.ofDays(1));

        MvcResult reuse = ok(b, req(List.of()));
        assertThat((Boolean) read(reuse, "$.cached")).isTrue();
        assertThat((Integer) read(reuse, "$.remainingToday")).isZero();

        MvcResult other =
                api.suggest(
                        b.session(),
                        b.postId(),
                        req(AiSuggestApi.LONG_BODY + NEW_PARAGRAPH, List.of(), false));
        assertThat(status(other)).isEqualTo(429);
        assertThat((String) read(other, "$.code")).isEqualTo("AI_DAILY_LIMIT");
        assertThat(fakeAi.totalCalls()).isEqualTo(1);
    }
}
