package com.team.blog.discovery.integration;

import static com.team.blog.discovery.support.SearchApi.body;
import static com.team.blog.discovery.support.SearchApi.ids;
import static com.team.blog.discovery.support.SearchApi.read;
import static com.team.blog.discovery.support.SearchApi.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.discovery.application.search.SearchQueryParser;
import com.team.blog.discovery.application.search.SearchStage;
import com.team.blog.discovery.infra.PostSearchRepository;
import com.team.blog.discovery.infra.PostSearchRepository.Hit;
import com.team.blog.discovery.infra.PostSearchRepository.StageResult;
import com.team.blog.discovery.support.SearchApi;
import com.team.blog.discovery.support.SearchFixtures;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import com.team.blog.support.fixture.PostFixtures.State;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/** 글 검색 (012 T010, US1 #1~#7, SC-003·SC-004·SC-005, FR-017~035). */
class PostSearchIT extends IntegrationTestBase {

    @Autowired PostSearchRepository repository;
    @Autowired SearchQueryParser parser;

    private long author;
    private Instant base;

    @BeforeEach
    void setUp() {
        author = members().member().handle("searcher").create();
        base = Instant.now().minus(Duration.ofDays(3)).truncatedTo(ChronoUnit.MICROS);
    }

    private SearchFixtures search() {
        return new SearchFixtures(jdbc);
    }

    private SearchApi api() {
        return new SearchApi(mockMvc);
    }

    private long post(String title, String content, int minutes, String... tags) {
        return search().post(author)
                .title(title)
                .content(content)
                .tags(tags)
                .firstPublicAt(base.plus(Duration.ofMinutes(minutes)))
                .create();
    }

    @Test
    void US1_1_제목_먼저_본문은_뒤_비공개는_없음() throws Exception {
        long titled = post("트랜잭션 격리 수준", "본문", 0);
        long bodyOnly = post("데이터베이스 이야기", "오늘은 트랜잭션을 다룬다", 10);
        long tagged = post("스프링 이야기", "본문", 5, "트랜잭션");
        search().post(author).title("트랜잭션 비공개").state(State.PUBLISHED_PRIVATE).create();

        MvcResult result = api().posts("트랜잭");

        assertThat(status(result)).as(body(result)).isEqualTo(200);
        assertThat(ids(result)).containsExactly(titled, tagged, bodyOnly);
        assertThat((Object) read(result, "$.notice")).isNull();
        assertThat((Object) read(result, "$.nextCursor")).isNull();
        assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("private, no-cache");
        // 카드 필드 + snippet (평평하게)
        Map<String, Object> first = read(result, "$.items[0]");
        assertThat(first)
                .containsKeys(
                        "id",
                        "url",
                        "title",
                        "excerpt",
                        "thumbnailUrl",
                        "firstPublicAt",
                        "commentCount",
                        "likeCount",
                        "author",
                        "snippet");
        assertThat((String) read(result, "$.items[2].snippet.text")).isEqualTo("오늘은 트랜잭션을 다룬다");
        List<List<Integer>> marks = read(result, "$.items[2].snippet.marks");
        assertThat(marks).containsExactly(List.of(4, 7));
    }

    @Test
    void US1_2_두글자는_제목과_태그만_notice() throws Exception {
        long titled = post("롬복 사용기", "본문", 0);
        long tagged = post("자바 도구", "본문", 1, "롬복");
        post("자바 이야기", "본문에만 롬복이 있다", 2);

        MvcResult result = api().posts("롬복");

        assertThat(ids(result)).containsExactly(titled, tagged);
        assertThat((String) read(result, "$.notice")).isEqualTo("TWO_CHAR_TITLE_TAG_ONLY");
    }

    @Test
    void US1_3_한글자는_무시하고_모든_단어_AND() throws Exception {
        long both = post("트랜잭션 정리", "본문", 0);
        post("트랜잭션 이야기", "본문", 1);
        post("정리 습관", "본문", 2);
        long bothSpread = post("정리 노트", "트랜잭션 전파", 3);

        MvcResult result = api().posts("트랜잭션 정리 a");

        assertThat(ids(result)).containsExactly(both, bothSpread);
        assertThat((String) read(result, "$.notice")).isEqualTo("TWO_CHAR_TITLE_TAG_ONLY");
    }

    @Test
    void US1_4_25개를_9개씩_끝까지_중복_누락_0_단계_경계가_페이지_중간() throws Exception {
        List<Long> expected = new ArrayList<>();
        // ① 제목 5개, ② 태그 6개, ③ 본문 14개 — 단계 경계가 1·2쪽 중간에 온다. 같은 시각 글도 섞는다
        List<Long> titled = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            titled.add(0, post("검색엔진 제목 " + i, "본문", i / 2));
        }
        List<Long> tagged = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            tagged.add(0, post("태그 글 " + i, "본문", 100 + i / 2, "검색엔진"));
        }
        List<Long> bodies = new ArrayList<>();
        for (int i = 0; i < 14; i++) {
            bodies.add(0, post("본문 글 " + i, "이 글은 검색엔진을 설명한다 " + i, 200 + i / 3));
        }
        // 같은 시각이면 번호 큰 순 — post()가 만든 순서대로 번호가 커진다
        expected.addAll(sortDesc(titled));
        expected.addAll(sortDesc(tagged));
        expected.addAll(sortDesc(bodies));

        List<Long> pages = new ArrayList<>();
        List<Integer> sizes = new ArrayList<>();
        String cursor = null;
        do {
            MvcResult page = api().posts("검색엔진", null, cursor, null);
            assertThat(status(page)).as(body(page)).isEqualTo(200);
            sizes.add(ids(page).size());
            pages.addAll(ids(page));
            cursor = read(page, "$.nextCursor");
        } while (cursor != null);

        assertThat(sizes).containsExactly(9, 9, 7);
        assertThat(pages).doesNotHaveDuplicates().containsExactlyElementsOf(expected);
    }

    private List<Long> sortDesc(List<Long> ids) {
        return jdbc.queryForList(
                "SELECT id FROM post WHERE id = ANY(?) ORDER BY first_public_at DESC, id DESC",
                Long.class,
                (Object) ids.toArray(Long[]::new));
    }

    @Test
    void US1_5_스크립트는_글자_그대로_SC005() throws Exception {
        post("안전 시험", "앞 <script>alert(1)</script> 트랜잭션 <img src=x onerror=alert(1)>", 0);

        MvcResult result = api().posts("트랜잭션");

        String text = read(result, "$.items[0].snippet.text");
        assertThat(text)
                .contains("<script>alert(1)</script>")
                .contains("<img src=x onerror=alert(1)>");
        assertThat(body(result)).doesNotContain("<mark");
    }

    @Test
    void FR023_코드_블록_안_글자도_찾는다() throws Exception {
        long code = post("코드 예제", "설명\n\n```java\nTransactionTemplate template;\n```\n", 0);

        assertThat(ids(api().posts("TransactionTemplate"))).containsExactly(code);
        assertThat(ids(api().posts("transactiontemplate"))).containsExactly(code);
    }

    @Test
    void US1_6_특수문자는_글자_그대로() throws Exception {
        long percent = post("100% 할인 행사", "본문", 0);
        post("1000 할인 행사", "본문", 1);
        long snake = post("snake_case 규칙", "본문", 2);
        post("snakeXcase 규칙", "본문", 3);
        long backslash = post("경로 a\\b 표기", "본문", 4);
        post("경로 ab 표기", "본문", 5);

        assertThat(ids(api().posts("100% 할인"))).containsExactly(percent);
        assertThat(ids(api().posts("snake_case"))).containsExactly(snake);
        assertThat(ids(api().posts("a\\b"))).containsExactly(backslash);
    }

    @Test
    void US1_7_결과_없음은_빈_배열() throws Exception {
        post("다른 글", "본문", 0);
        MvcResult result = api().posts("롬복");
        assertThat(status(result)).isEqualTo(200);
        assertThat(ids(result)).isEmpty();
        assertThat((Object) read(result, "$.nextCursor")).isNull();
        assertThat((String) read(result, "$.notice")).isEqualTo("TWO_CHAR_TITLE_TAG_ONLY");
    }

    @Test
    void 최신순은_단계_없이_최초_공개_최신순() throws Exception {
        long titled = post("트랜잭션 제목", "본문", 0);
        long bodyOnly = post("다른 제목", "트랜잭션 본문", 10);
        long tagged = post("태그 글", "본문", 5, "트랜잭션");

        assertThat(ids(api().posts("트랜잭션", "latest", null, null)))
                .containsExactly(bodyOnly, tagged, titled);
        assertThat(api().allPostIds("트랜잭션", "latest", null))
                .containsExactly(bodyOnly, tagged, titled);
    }

    @Test
    void 다른_검색어나_정렬의_커서는_400() throws Exception {
        for (int i = 0; i < 12; i++) {
            post("커서 시험 " + i, "본문", i);
        }
        String cursor = read(api().posts("커서 시험"), "$.nextCursor");
        assertThat(cursor).isNotNull();

        MvcResult otherQuery = api().posts("커서", null, cursor, null);
        MvcResult otherSort = api().posts("커서 시험", "latest", cursor, null);
        MvcResult broken = api().posts("커서 시험", null, "not-a-cursor", null);

        for (MvcResult result : List.of(otherQuery, otherSort, broken)) {
            assertThat(status(result)).isEqualTo(400);
            assertThat((String) read(result, "$.code")).isEqualTo("INVALID_CURSOR");
        }
        assertThat(status(api().posts("커서 시험", null, cursor, null))).isEqualTo(200);
    }

    @Test
    void SC003_비공개_휴지통_숨김_유예작성자_글은_0() throws Exception {
        long visible = post("노출시험 공개", "본문", 0);
        for (State state :
                List.of(State.PUBLISHED_PRIVATE, State.DRAFT, State.TRASHED, State.HIDDEN)) {
            search().post(author).title("노출시험 " + state).state(state).create();
        }
        long other = members().member().create();
        search().post(other).title("노출시험 유예").state(State.AUTHOR_WITHDRAWN).create();
        // 작업본에만 있는 제목은 찾지 않는다 (발행된 내용만)
        search().post(author).title("노출시험 수정중").state(State.EDITING).create();
        long editing = jdbc.queryForObject("SELECT max(id) FROM post", Long.class);

        assertThat(ids(api().posts("노출시험"))).containsExactlyInAnyOrder(visible, editing);
        assertThat(ids(api().posts("고치는 중인 제목"))).isEmpty();
    }

    @Test
    void 작성자_본인과_관리자가_검색해도_비공개_글은_0() throws Exception {
        search().post(author).title("나만의 비밀글").state(State.PUBLISHED_PRIVATE).create();
        search().post(author).title("나만의 숨긴글").state(State.HIDDEN).create();
        long admin = members().member().role("ADMIN").create();
        Cookie mine = TestLogin.loginAs(mockMvc, author);
        Cookie adminSession = TestLogin.loginAs(mockMvc, admin);

        assertThat(ids(api().as(mine).posts("나만의"))).isEmpty();
        assertThat(ids(api().as(adminSession).posts("나만의"))).isEmpty();
    }

    @Test
    void 최근창_밖의_글도_후보_조회로_찾는다() throws Exception {
        long old = post("오래된 트랜잭션 글", "본문", 0);
        long oldTag = post("오래된 태그 글", "본문", 1, "트랜잭션");
        long oldBody = post("오래된 본문 글", "트랜잭션 설명", 2);
        for (int i = 0; i < 8; i++) {
            post("최근 다른 글 " + i, "본문", 100 + i);
        }
        var query = parser.parse("트랜잭션");

        StageResult title = repository.find(query, SearchStage.TITLE, null, null, 10, 5);
        StageResult tag = repository.find(query, SearchStage.TITLE_OR_TAG, null, null, 10, 5);
        StageResult content = repository.find(query, SearchStage.ANYWHERE, null, null, 10, 5);
        StageResult latest = repository.find(query, SearchStage.ANY, null, null, 10, 5);

        assertThat(title.usedCandidates()).isTrue();
        assertThat(title.hits()).extracting(Hit::id).containsExactly(old);
        assertThat(tag.hits()).extracting(Hit::id).containsExactly(oldTag);
        assertThat(content.hits()).extracting(Hit::id).containsExactly(oldBody);
        assertThat(latest.hits()).extracting(Hit::id).containsExactly(oldBody, oldTag, old);
        // 창이 넉넉하면 후보 조회 없이 SQL 1번
        StageResult wide = repository.find(query, SearchStage.ANY, null, null, 10, 3000);
        assertThat(wide.usedCandidates()).isFalse();
        assertThat(wide.sqlCount()).isEqualTo(1);
        assertThat(wide.hits()).extracting(Hit::id).containsExactly(oldBody, oldTag, old);
    }

    @Test
    void 남는_단어가_없으면_400_SEARCH_QUERY_TOO_SHORT() throws Exception {
        for (String q : new String[] {"a b c", "", " ", "가"}) {
            MvcResult result = api().posts(q);
            assertThat(status(result)).as(q).isEqualTo(400);
            assertThat(body(result))
                    .isEqualTo(
                            "{\"code\":\"SEARCH_QUERY_TOO_SHORT\",\"message\":\"두 글자 이상 입력해 주세요\","
                                    + "\"errors\":[],\"details\":null}");
        }
        assertThat(status(api().posts(null))).isEqualTo(400);
    }

    @Test
    void 모르는_정렬은_400() throws Exception {
        MvcResult result = api().posts("트랜잭션", "popular", null, null);
        assertThat(status(result)).isEqualTo(400);
    }
}
