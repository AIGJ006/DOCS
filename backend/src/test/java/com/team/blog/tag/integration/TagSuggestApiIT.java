package com.team.blog.tag.integration;

import static com.team.blog.tag.support.TagApi.body;
import static com.team.blog.tag.support.TagApi.read;
import static com.team.blog.tag.support.TagApi.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.account.application.AiConsentService;
import com.team.blog.account.application.policy.ReservedWords;
import com.team.blog.post.application.PostOwnershipQuery;
import com.team.blog.shared.error.ApiException;
import com.team.blog.shared.security.AccountStatusGuard;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import com.team.blog.support.ai.FakeAiConfiguration.FakeAi;
import com.team.blog.support.fixture.PostFixtures;
import com.team.blog.tag.application.suggest.DailyUsage;
import com.team.blog.tag.application.suggest.FailureKind;
import com.team.blog.tag.application.suggest.PopularTagProvider;
import com.team.blog.tag.application.suggest.SuggestCache;
import com.team.blog.tag.application.suggest.SuggestInputCleaner;
import com.team.blog.tag.application.suggest.SuggestOutcome;
import com.team.blog.tag.application.suggest.SuggestPostLimits;
import com.team.blog.tag.application.suggest.SuggestResultFilter;
import com.team.blog.tag.application.suggest.TagSuggestInput;
import com.team.blog.tag.application.suggest.TagSuggestProperties;
import com.team.blog.tag.application.suggest.TagSuggestRequest;
import com.team.blog.tag.application.suggest.TagSuggestService;
import com.team.blog.tag.application.suggest.TagSuggesterRouter;
import com.team.blog.tag.support.AiSuggestApi;
import com.team.blog.tag.support.TagFixtures;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 추천 API (013 T021, US1 #1~#5, SC-003·SC-007, FR-005·FR-012·FR-030, research R4 판정 순서). AI는 가짜 공급자.
 */
class TagSuggestApiIT extends IntegrationTestBase {

    @Autowired private FakeAi fakeAi;
    @Autowired private DailyUsage usage;

    @Autowired
    @Qualifier("aiTagSuggestService")
    private TagSuggestService service;

    @Autowired private AccountStatusGuard guard;
    @Autowired private PostOwnershipQuery ownership;
    @Autowired private AiConsentService consentService;
    @Autowired private SuggestInputCleaner cleaner;
    @Autowired private SuggestResultFilter filter;
    @Autowired private SuggestCache cache;
    @Autowired private PopularTagProvider popular;
    @Autowired private TagSuggesterRouter router;
    @Autowired private TagSuggestProperties properties;
    @Autowired private SuggestPostLimits limits;
    @Autowired private Clock clock;

    private AiSuggestApi api;

    @BeforeEach
    void setUp() {
        fakeAi.reset();
        api = new AiSuggestApi(mockMvc, jdbc);
    }

    private PostFixtures posts() {
        return new PostFixtures(jdbc);
    }

    private Cookie login(long memberId) {
        return TestLogin.loginAs(mockMvc, memberId);
    }

    /** 동의한 인증 작성자와 그 공개 글. */
    private record Author(long id, long postId, Cookie session) {}

    private Author author(PostFixtures.State state) {
        long id = members().member().create();
        api.consent(id);
        return new Author(id, posts().create(id, state), login(id));
    }

    private Author author() {
        return author(PostFixtures.State.PUBLISHED_PUBLIC);
    }

    private String usageKey(long memberId) {
        return "ai:tag:usage:"
                + memberId
                + ":"
                + LocalDate.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.BASIC_ISO_DATE);
    }

    private static List<String> tags(MvcResult r) {
        return read(r, "$.tags");
    }

    private static List<String> tags(String... names) {
        return List.of(names);
    }

    private static Map<String, Object> bodyWith(List<String> current, String extra) {
        return AiSuggestApi.body("JPA N+1 정리", AiSuggestApi.LONG_BODY + extra, current, false);
    }

    // ---- US1 #1~#3 ----

    @Test
    void 추천은_5개_이하이고_post_tag는_바뀌지_않는다() throws Exception {
        Author a = author();
        new TagFixtures(jdbc).attach(a.postId(), "jpa");
        List<Map<String, Object>> before =
                jdbc.queryForList("SELECT * FROM post_tag WHERE post_id = ?", a.postId());

        MvcResult r = api.suggest(a.session(), a.postId(), AiSuggestApi.body(tags("jpa")));

        assertThat(status(r)).as(body(r)).isEqualTo(200);
        assertThat(tags(r)).containsExactly("spring", "hibernate", "java", "database");
        assertThat((String) read(r, "$.provider")).isEqualTo("GEMINI");
        assertThat((Boolean) read(r, "$.cached")).isFalse();
        assertThat((Boolean) read(r, "$.truncated")).isFalse();
        assertThat((Integer) read(r, "$.remainingToday")).isEqualTo(19);
        assertThat(fakeAi.gemini().calls()).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT * FROM post_tag WHERE post_id = ?", a.postId()))
                .isEqualTo(before);
        assertThat(redis.opsForValue().get(usageKey(a.id()))).isEqualTo("1");
    }

    @Test
    void 붙인_태그가_8개면_2개() throws Exception {
        Author a = author();
        List<String> eight = IntStream.range(0, 8).mapToObj(i -> "t" + i).toList();
        MvcResult r = api.suggest(a.session(), a.postId(), AiSuggestApi.body(eight));
        assertThat(status(r)).as(body(r)).isEqualTo(200);
        assertThat(tags(r)).containsExactly("spring", "jpa");
    }

    @Test
    void 붙인_태그가_10개면_AI를_부르지_않고_빈_목록() throws Exception {
        Author a = author();
        List<String> ten = IntStream.range(0, 10).mapToObj(i -> "t" + i).toList();
        MvcResult r = api.suggest(a.session(), a.postId(), AiSuggestApi.body(ten));
        assertThat(status(r)).as(body(r)).isEqualTo(200);
        assertThat(tags(r)).isEmpty();
        assertThat((Integer) read(r, "$.remainingToday")).isEqualTo(20);
        assertThat(fakeAi.totalCalls()).isZero();
        assertThat(redis.opsForValue().get(usageKey(a.id()))).isNull();
    }

    @Test
    void 이미_붙인_태그_금칙어_형식_위반_중복을_뺀다() throws Exception {
        String banned =
                ReservedWords.readList(new ClassPathResource("policy/banned-words.txt"))
                        .iterator()
                        .next();
        Author a = author();
        fakeAi.gemini()
                .respondTags("Spring Boot", "spring-boot", " JPA ", "!!!", banned, "#Docker");
        MvcResult r =
                api.suggest(a.session(), a.postId(), AiSuggestApi.body(tags("jpa", "Kotlin")));
        assertThat(status(r)).as(body(r)).isEqualTo(200);
        assertThat(tags(r)).containsExactly("spring-boot", "docker");
        // 저장소에는 붙인 태그를 빼기 전 목록이 남는다
        assertThat(fakeAi.gemini().lastInput().currentTags()).containsExactly("jpa", "kotlin");
    }

    @Test
    void 결과가_다_걸러져도_횟수는_하나_줄고_저장하지_않는다() throws Exception {
        Author a = author();
        fakeAi.gemini().respondTags("jpa", "!!!");
        MvcResult r = api.suggest(a.session(), a.postId(), AiSuggestApi.body(tags("jpa")));
        assertThat(status(r)).as(body(r)).isEqualTo(200);
        assertThat(tags(r)).isEmpty();
        assertThat((Integer) read(r, "$.remainingToday")).isEqualTo(19);

        fakeAi.gemini().respondTags("!!!");
        // [다시 추천]이라 비슷한 내용 재사용을 건너뛰고 새로 부른다
        MvcResult empty =
                api.suggest(
                        a.session(),
                        a.postId(),
                        AiSuggestApi.body(
                                "JPA N+1 정리", AiSuggestApi.LONG_BODY + " 끝", List.of(), true));
        assertThat(tags(empty)).isEmpty();
        assertThat(redis.keys("ai:tag:post:*")).hasSize(1); // 첫 요청의 jpa만 저장됨
        assertThat(redis.opsForValue().get(usageKey(a.id()))).isEqualTo("2");
    }

    // ---- US1 #4: 422 ----

    @Test
    void 정리_후_99자면_422이고_AI를_부르지_않는다() throws Exception {
        Author a = author();
        String body = "가".repeat(97); // "나" + 공백 + 97자 = 99자 (그림은 통째로 빠진다)
        MvcResult r =
                api.suggest(
                        a.session(),
                        a.postId(),
                        AiSuggestApi.body("나", "![그림](http://x/a.png) " + body, List.of(), false));
        assertThat(status(r)).as(body(r)).isEqualTo(422);
        assertThat((String) read(r, "$.code")).isEqualTo("CONTENT_TOO_SHORT");
        assertThat((Map<String, Object>) read(r, "$.details"))
                .isEqualTo(Map.of("minChars", 100, "length", 99));
        assertThat(fakeAi.totalCalls()).isZero();
        assertThat(redis.opsForValue().get(usageKey(a.id()))).isNull();
    }

    // ---- 판정 순서 ----

    @Test
    void 비회원은_401() throws Exception {
        long id = members().member().create();
        long post = posts().create(id, PostFixtures.State.PUBLISHED_PUBLIC);
        MvcResult r = api.suggest(null, post);
        assertThat(status(r)).isEqualTo(401);
        assertThat((String) read(r, "$.code")).isEqualTo("LOGIN_REQUIRED");
        assertThat(status(api.status(null, post))).isEqualTo(401);
    }

    @Test
    void 인증_전_작성자는_404보다_먼저_403() throws Exception {
        long id = members().member().emailVerified(false).create();
        api.consent(id);
        Cookie session = login(id);
        long other = members().member().create();
        long othersPost = posts().create(other, PostFixtures.State.PUBLISHED_PUBLIC);
        MvcResult r = api.suggest(session, othersPost);
        assertThat(status(r)).isEqualTo(403);
        assertThat((String) read(r, "$.code")).isEqualTo("EMAIL_NOT_VERIFIED");
    }

    @Test
    void 남의_글_휴지통_글_없는_글_숫자가_아닌_번호는_같은_404() throws Exception {
        Author a = author();
        long other = members().member().create();
        long othersPost = posts().create(other, PostFixtures.State.PUBLISHED_PUBLIC);
        long trashed = posts().create(a.id(), PostFixtures.State.TRASHED);
        // 본문까지 틀려도 404가 먼저
        Map<String, Object> bad = AiSuggestApi.body("제".repeat(101), "", List.of(), false);
        for (Object target : List.of(othersPost, trashed, posts().nonexistentId(), "abc", "0")) {
            MvcResult r = api.suggest(a.session(), target, bad);
            assertThat(status(r)).as(String.valueOf(target)).isEqualTo(404);
            assertThat(body(r))
                    .isEqualTo(
                            "{\"code\":\"NOT_FOUND\",\"message\":\"볼 수 없는 페이지예요\",\"errors\":[],\"details\":null}");
            assertThat(status(api.status(a.session(), target))).isEqualTo(404);
        }
        assertThat(fakeAi.totalCalls()).isZero();
    }

    @Test
    void 기능이_꺼지면_동의보다_먼저_503_DISABLED() {
        long id = members().member().create(); // 동의 없음
        long post = posts().create(id, PostFixtures.State.PUBLISHED_PUBLIC);
        TagSuggestProperties off =
                new TagSuggestProperties(
                        false,
                        properties.promptVersion(),
                        properties.minInputChars(),
                        properties.maxSuggestions(),
                        properties.dailyLimitPerMember(),
                        properties.cache(),
                        properties.popular(),
                        properties.gemini(),
                        properties.ollama());
        TagSuggestService disabled =
                new TagSuggestService(
                        guard,
                        ownership,
                        consentService,
                        cleaner,
                        filter,
                        cache,
                        usage,
                        popular,
                        router,
                        off,
                        limits,
                        clock);
        TagSuggestRequest request = new TagSuggestRequest("t", "짧음", List.of(), false);

        assertApi(
                () -> disabled.suggest(id, String.valueOf(post), request),
                503,
                "AI_UNAVAILABLE",
                Map.of("reason", "DISABLED"));
        long other = members().member().create();
        assertApi(
                () -> disabled.suggest(other, String.valueOf(post), request),
                404,
                "NOT_FOUND",
                null);
        assertThat(fakeAi.totalCalls()).isZero();
    }

    private static void assertApi(
            ThrowableAssert.ThrowingCallable call,
            int status,
            String code,
            Map<String, Object> details) {
        org.assertj.core.api.Assertions.assertThatThrownBy(call)
                .isInstanceOfSatisfying(
                        ApiException.class,
                        e -> {
                            assertThat(e.status().value()).isEqualTo(status);
                            assertThat(e.reasonCode().code()).isEqualTo(code);
                            if (details != null) {
                                assertThat(e.details()).isEqualTo(details);
                            }
                        });
    }

    @Test
    void 동의가_없으면_본문_검사보다_먼저_409() throws Exception {
        long id = members().member().create();
        long post = posts().create(id, PostFixtures.State.PUBLISHED_PUBLIC);
        MvcResult r =
                api.suggest(
                        login(id), post, AiSuggestApi.body("제".repeat(101), "", List.of(), false));
        assertThat(status(r)).as(body(r)).isEqualTo(409);
        assertThat((String) read(r, "$.code")).isEqualTo("AI_CONSENT_REQUIRED");
        assertThat((Map<String, Object>) read(r, "$.details"))
                .isEqualTo(Map.of("version", "2026-10-08"));
        assertThat(fakeAi.totalCalls()).isZero();
    }

    @Test
    void 제목_101자는_422보다_먼저_400() throws Exception {
        Author a = author();
        MvcResult r =
                api.suggest(
                        a.session(),
                        a.postId(),
                        AiSuggestApi.body("제".repeat(101), "짧음", List.of(), false));
        assertThat(status(r)).as(body(r)).isEqualTo(400);
        assertThat((String) read(r, "$.code")).isEqualTo("VALIDATION_FAILED");
        assertThat((String) read(r, "$.errors[0].field")).isEqualTo("title");
        assertThat((String) read(r, "$.errors[0].code")).isEqualTo("TITLE_TOO_LONG");

        List<String> eleven = IntStream.range(0, 11).mapToObj(i -> "t" + i).toList();
        MvcResult tooMany = api.suggest(a.session(), a.postId(), AiSuggestApi.body(eleven));
        assertThat(status(tooMany)).isEqualTo(400);
        assertThat((String) read(tooMany, "$.errors[0].code")).isEqualTo("TOO_MANY_TAGS");
        assertThat(fakeAi.totalCalls()).isZero();
    }

    // ---- 하루 한도 ----

    @Test
    void 스물한_번째는_429_AI_DAILY_LIMIT() throws Exception {
        Author a = author();
        redis.opsForValue().set(usageKey(a.id()), "20");
        MvcResult r = api.suggest(a.session(), a.postId());
        assertThat(status(r)).as(body(r)).isEqualTo(429);
        assertThat((String) read(r, "$.code")).isEqualTo("AI_DAILY_LIMIT");
        String resetAt = read(r, "$.details.resetAt");
        Instant reset = Instant.parse(resetAt);
        assertThat(reset.atZone(ZoneId.of("Asia/Seoul")).toLocalTime().toSecondOfDay()).isZero();
        long retry = Long.parseLong(r.getResponse().getHeader("Retry-After"));
        assertThat(retry).isBetween(1L, 86_400L);
        assertThat(fakeAi.totalCalls()).isZero();
        assertThat(redis.opsForValue().get(usageKey(a.id()))).isEqualTo("20");
    }

    @Test
    void 공급자가_실패한_요청은_횟수를_되돌린다() throws Exception {
        Author a = author();
        fakeAi.gemini().respond(new SuggestOutcome.Failed(FailureKind.MALFORMED));
        MvcResult r = api.suggest(a.session(), a.postId());
        assertThat(status(r)).as(body(r)).isEqualTo(503);
        assertThat((String) read(r, "$.details.reason")).isEqualTo("FAILED");
        assertThat(redis.opsForValue().get(usageKey(a.id()))).isEqualTo("0");
        assertThat(redis.keys("ai:tag:v*")).isEmpty();
    }

    // ---- FR-030, FR-012 ----

    @Test
    void 비공개_글은_외부_AI를_부르지_않는다() throws Exception {
        Author a = author(PostFixtures.State.PUBLISHED_PRIVATE);
        MvcResult r = api.suggest(a.session(), a.postId());
        assertThat(status(r)).as(body(r)).isEqualTo(200);
        assertThat((String) read(r, "$.provider")).isEqualTo("OLLAMA");
        assertThat(fakeAi.gemini().calls()).isZero();
        assertThat(fakeAi.ollama().calls()).isEqualTo(1);

        Author draft = author(PostFixtures.State.DRAFT);
        jdbc.update("UPDATE post SET visibility = 'PRIVATE' WHERE id = ?", draft.postId());
        api.suggest(draft.session(), draft.postId(), bodyWith(List.of(), " 임시"));
        assertThat(fakeAi.gemini().calls()).isZero();
    }

    @Test
    void 공급자에_넘긴_입력에_회원_정보가_없다() throws Exception {
        long id = members().member().nickname("비밀닉네임").email("secret.person@example.com").create();
        api.consent(id);
        long post = posts().create(id, PostFixtures.State.PUBLISHED_PUBLIC);
        assertThat(status(api.suggest(login(id), post))).isEqualTo(200);
        TagSuggestInput sent = fakeAi.gemini().lastInput();
        String all = sent.text() + sent.popularTags() + sent.currentTags();
        assertThat(all).doesNotContain("비밀닉네임", "secret.person", String.valueOf(id) + "@");
        assertThat(sent.text()).startsWith("JPA N+1 정리 JPA N+1 문제 Spring Boot에서 @OneToMany");
    }

    // ---- 상태 API ----

    @Test
    void 상태_API는_버튼_표시_동의_예측_공급자를_알려준다() throws Exception {
        Author a = author();
        MvcResult ok = api.status(a.session(), a.postId());
        assertThat(status(ok)).isEqualTo(200);
        assertThat(body(ok))
                .isEqualTo(
                        "{\"available\":true,\"consentRequired\":false,\"consentVersion\":\"2026-10-08\",\"provider\":\"GEMINI\",\"remainingToday\":20}");

        long noConsent = members().member().create();
        long post = posts().create(noConsent, PostFixtures.State.PUBLISHED_PRIVATE);
        MvcResult need = api.status(login(noConsent), post);
        assertThat((Boolean) read(need, "$.consentRequired")).isTrue();
        assertThat((String) read(need, "$.provider")).isEqualTo("OLLAMA");

        fakeAi.ollama().configured(false);
        MvcResult none = api.status(login(noConsent), post);
        assertThat(body(none))
                .isEqualTo(
                        "{\"available\":false,\"consentRequired\":true,\"consentVersion\":\"2026-10-08\",\"provider\":null,\"remainingToday\":20}");
    }
}
