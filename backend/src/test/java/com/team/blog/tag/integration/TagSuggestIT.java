package com.team.blog.tag.integration;

import static com.team.blog.tag.support.TagApi.body;
import static com.team.blog.tag.support.TagApi.read;
import static com.team.blog.tag.support.TagApi.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.account.application.policy.ReservedWords;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.RedisOutage;
import com.team.blog.support.TestLogin;
import com.team.blog.support.fixture.PostFixtures;
import com.team.blog.tag.application.TagSuggestService;
import com.team.blog.tag.application.TagSuggestionView;
import com.team.blog.tag.support.TagApi;
import com.team.blog.tag.support.TagFixtures;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 태그 자동완성 (008 T040, US3 #1~#3, FR-033, research R11). 후보는 ① 내 글 전부(상태·공개 범위 무관)의 태그 ② 공개 글 수 1 이상인
 * 태그이고, 순서는 내 태그 → 공개 글 수 → 이름. 회원당 1분 60번.
 */
class TagSuggestIT extends IntegrationTestBase {

    @Autowired private TagSuggestService suggestService;

    private TagApi api() {
        return new TagApi(mockMvc);
    }

    private TagFixtures tags() {
        return new TagFixtures(jdbc);
    }

    private PostFixtures posts() {
        return new PostFixtures(jdbc);
    }

    private long publicPost(long author, String... tagNames) {
        long id = posts().create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        tags().attach(id, tagNames);
        return id;
    }

    private Cookie login(long memberId) {
        return TestLogin.loginAs(mockMvc, memberId);
    }

    private List<Map<String, Object>> suggest(Cookie session, String q) throws Exception {
        MvcResult result = api().suggest(session, q);
        assertThat(status(result)).as(body(result)).isEqualTo(200);
        return read(result, "$");
    }

    private static List<Object> names(List<Map<String, Object>> items) {
        return items.stream().map(i -> i.get("name")).toList();
    }

    @Test
    void 내_태그가_먼저_그다음_공개_글_수_순() throws Exception {
        long me = members().member().create();
        long other = members().member().create();
        // 내 태그: spring-boot (내 비공개 글 1개 + 공개 1개 → 공개 수 1)
        tags().attach(posts().create(me, PostFixtures.State.PUBLISHED_PRIVATE), "spring-boot");
        publicPost(me, "spring-boot");
        // 남의 공개 글: spring 3개, spring-data 2개, spring-batch 2개(이름 순으로 batch가 먼저)
        for (int i = 0; i < 3; i++) {
            publicPost(other, "spring");
        }
        for (int i = 0; i < 2; i++) {
            publicPost(other, "spring-data", "spring-batch");
        }
        // 10개를 넘게 만든다
        for (int i = 0; i < 10; i++) {
            publicPost(other, "spring-x" + i);
        }

        List<Map<String, Object>> items = suggest(login(me), "Spr");

        assertThat(items).hasSize(10);
        assertThat(names(items).subList(0, 4))
                .containsExactly("spring-boot", "spring", "spring-batch", "spring-data");
        assertThat(items.get(0)).containsEntry("mine", true).containsEntry("postCount", 1);
        assertThat(items.get(1)).containsEntry("mine", false).containsEntry("postCount", 3);
        assertThat(names(items).subList(4, 10))
                .containsExactly(
                        "spring-x0",
                        "spring-x1",
                        "spring-x2",
                        "spring-x3",
                        "spring-x4",
                        "spring-x5");
    }

    @Test
    void 남의_비공개_전용_태그는_제안하지_않는다() throws Exception {
        long me = members().member().create();
        long other = members().member().create();
        tags().attach(posts().create(other, PostFixtures.State.PUBLISHED_PRIVATE), "secret-plan");
        tags().attach(posts().create(other, PostFixtures.State.DRAFT), "secret-draft");

        assertThat(suggest(login(me), "secret")).isEmpty();
    }

    @Test
    void 내_비공개_글_태그는_제안한다() throws Exception {
        long me = members().member().create();
        tags().attach(posts().create(me, PostFixtures.State.PUBLISHED_PRIVATE), "secret-plan");
        tags().attach(posts().create(me, PostFixtures.State.DRAFT), "secret-draft");

        List<Map<String, Object>> items = suggest(login(me), "secret");

        assertThat(names(items)).containsExactly("secret-draft", "secret-plan");
        assertThat(items).allSatisfy(i -> assertThat(i).containsEntry("mine", true));
        assertThat(items).allSatisfy(i -> assertThat(i).containsEntry("postCount", 0));
    }

    @Test
    void 비회원은_401_인증_전은_허용() throws Exception {
        long author = members().member().create();
        publicPost(author, "java");

        MvcResult anonymous = api().suggest(null, "ja");
        assertThat(status(anonymous)).isEqualTo(401);
        assertThat(TagApi.<String>read(anonymous, "$.code")).isEqualTo("LOGIN_REQUIRED");

        long unverified = members().member().emailVerified(false).create();
        assertThat(names(suggest(login(unverified), "ja"))).containsExactly("java");
    }

    @Test
    void 검색어_정리_결과가_비면_빈_목록() throws Exception {
        long me = members().member().create();
        publicPost(me, "java");
        Cookie session = login(me);

        for (String q : List.of("", "#", " ", "#  ", new String(Character.toChars(0x1F525)))) {
            assertThat(suggest(session, q)).as("q=[%s]", q).isEmpty();
        }
        MvcResult noParam = api().getRaw(session, "/api/tags/suggest");
        assertThat(status(noParam)).isEqualTo(200);
        assertThat(TagApi.<List<Object>>read(noParam, "$")).isEmpty();
    }

    @Test
    void 밑줄은_와일드카드가_아니다() throws Exception {
        long me = members().member().create();
        publicPost(me, "ab", "a_b");

        assertThat(names(suggest(login(me), "a_"))).containsExactly("a_b");
    }

    @Test
    void 금칙어_검색어도_거부하지_않는다() throws Exception {
        long me = members().member().create();
        String banned =
                List.copyOf(
                                ReservedWords.readList(
                                        new ClassPathResource("policy/banned-words.txt")))
                        .get(0);
        MvcResult result = api().suggest(login(me), banned);
        assertThat(status(result)).isEqualTo(200);
        assertThat(TagApi.<List<Object>>read(result, "$")).isEmpty();
    }

    @Test
    void 일분_61번째는_429() throws Exception {
        long me = members().member().create();
        Cookie session = login(me);
        for (int i = 0; i < 60; i++) {
            assertThat(status(api().suggest(session, "ja"))).as("call %d", i + 1).isEqualTo(200);
        }
        MvcResult over = api().suggest(session, "ja");
        assertThat(status(over)).isEqualTo(429);
        assertThat(TagApi.<String>read(over, "$.code")).isEqualTo("TOO_MANY_REQUESTS");
        assertThat(over.getResponse().getHeader("Retry-After")).isNotBlank();

        // 다른 회원은 따로 센다
        long other = members().member().create();
        assertThat(status(api().suggest(login(other), "ja"))).isEqualTo(200);
    }

    @Test
    void 정리_결과가_빈_검색어는_요청_제한을_세지_않는다() throws Exception {
        long me = members().member().create();
        Cookie session = login(me);
        for (int i = 0; i < 61; i++) {
            assertThat(status(api().suggest(session, "#"))).isEqualTo(200);
        }
        assertThat(status(api().suggest(session, "ja"))).isEqualTo(200);
    }

    @Test
    void Redis_장애면_제한_없이_응답() throws Exception {
        long me = members().member().create();
        publicPost(me, "java");
        // 세션 저장소도 Redis라 장애 중에는 로그인 요청 자체가 401이 된다 — 요청 제한 판정만 Service로 확인한다
        try (RedisOutage outage = RedisOutage.start()) {
            for (int i = 0; i < 61; i++) {
                assertThat(suggestService.suggest("ja", me))
                        .extracting(TagSuggestionView::name)
                        .containsExactly("java");
            }
        }
    }

    @Test
    void 탈퇴_신청_숨김_휴지통_글만_쓴_태그는_공개_수_0() throws Exception {
        long me = members().member().create();
        tags().attach(posts().create(me, PostFixtures.State.TRASHED), "only-trash");
        long other = members().member().create();
        tags().attach(posts().create(other, PostFixtures.State.HIDDEN), "only-hidden");
        long withdrawn = members().member().create();
        tags().attach(posts().create(withdrawn, PostFixtures.State.AUTHOR_WITHDRAWN), "only-gone");

        List<Map<String, Object>> mine = suggest(login(me), "only");
        assertThat(names(mine)).containsExactly("only-trash");
        assertThat(mine.get(0)).containsEntry("mine", true).containsEntry("postCount", 0);

        long viewer = members().member().create();
        assertThat(suggest(login(viewer), "only")).isEmpty();
    }
}
