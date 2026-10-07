package com.team.blog.discovery;

import static com.team.blog.discovery.support.ReadingApi.body;
import static com.team.blog.discovery.support.ReadingApi.cacheControl;
import static com.team.blog.discovery.support.ReadingApi.ids;
import static com.team.blog.discovery.support.ReadingApi.nextCursor;
import static com.team.blog.discovery.support.ReadingApi.read;
import static com.team.blog.discovery.support.ReadingApi.status;
import static com.team.blog.discovery.support.ReadingApi.titles;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.discovery.support.PostReadingFixture;
import com.team.blog.discovery.support.ReadingApi;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.SqlCounter;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** 홈 목록 {@code GET /api/posts} (005 T018, US1 #1~#3, Q-1~Q-3, SC-002·003·004). */
class HomeListIntegrationTest extends IntegrationTestBase {

    private PostReadingFixture fixture;

    @BeforeEach
    void load() {
        fixture = PostReadingFixture.load(jdbc);
    }

    private ReadingApi api() {
        return new ReadingApi(mockMvc);
    }

    @Test
    void 첫_페이지는_공개_노출_글_9개와_다음_위치_값() throws Exception {
        MvcResult result = api().getAs(null, "/api/posts?size=50");

        assertThat(status(result)).as(body(result)).isEqualTo(200);
        assertThat(titles(result))
                .containsExactlyElementsOf(PostReadingFixture.HOME_TITLES.subList(0, 9));
        assertThat(nextCursor(result)).isNotBlank();
        assertThat(cacheControl(result)).isEqualTo("private, no-cache");
        // 본문 필드 없음, 카드 형식 그대로
        assertThat(body(result)).doesNotContain("contentHtml").doesNotContain("contentMd");
        assertThat((String) read(result, "$.items[0].url"))
                .isEqualTo("/@kim755030/posts/" + fixture.postOf("A", "republished"));
        assertThat((String) read(result, "$.items[0].firstPublicAt"))
                .isEqualTo("2026-09-28T10:00:00.000001Z");
        assertThat((String) read(result, "$.items[0].author.handle")).isEqualTo("kim755030");
        assertThat((String) read(result, "$.items[0].author.profileImageUrl"))
                .isEqualTo("http://localhost:9000/blog/" + PostReadingFixture.A_PROFILE_THUMB_KEY);
        assertThat(((Number) read(result, "$.items[0].commentCount")).intValue()).isEqualTo(3);
        assertThat(((Number) read(result, "$.items[0].likeCount")).intValue()).isEqualTo(12);
    }

    @Test
    void 커서로_9_9_2를_받고_마지막_위치_값은_null() throws Exception {
        MvcResult first = api().home(null, null);
        MvcResult second = api().home(null, nextCursor(first));
        MvcResult third = api().home(null, nextCursor(second));

        assertThat(titles(first)).hasSize(9);
        assertThat(titles(second))
                .containsExactlyElementsOf(PostReadingFixture.HOME_TITLES.subList(9, 18));
        assertThat(titles(third))
                .containsExactlyElementsOf(PostReadingFixture.HOME_TITLES.subList(18, 20));
        assertThat(nextCursor(second)).isNotBlank();
        assertThat(nextCursor(third)).isNull();
    }

    @Test
    void 공개_글이_18개면_두_번째_응답에서_끝난다() throws Exception {
        fixture.trash(fixture.postByTitle("A12 글"));
        fixture.trash(fixture.postByTitle("B6 글"));

        MvcResult first = api().home(null, null);
        MvcResult second = api().home(null, nextCursor(first));

        assertThat(titles(second)).hasSize(9);
        assertThat(nextCursor(second)).isNull();
    }

    @Test
    void 같은_마이크로초_두_글이_페이지_경계에_걸려도_둘_다_나온다() throws Exception {
        MvcResult first = api().home(null, null);
        assertThat(titles(first)).endsWith("B 같은 시각 1");

        MvcResult second = api().home(null, nextCursor(first));

        assertThat(titles(second)).startsWith("B 같은 시각 2");
    }

    @Test
    void 보는_도중_발행_삭제_비공개_전환이_있어도_중복도_누락도_없다() throws Exception {
        MvcResult first = api().home(null, null);
        List<Long> seen = new ArrayList<>(ids(first));

        long fresh = fixture.publishNow("A", "방금 발행한 글");
        fixture.trash(seen.get(0)); // 1페이지에 이미 받은 글
        long hiddenLater = fixture.postByTitle("A07 글"); // 2페이지에 나올 글
        fixture.makePrivate(hiddenLater);

        String cursor = nextCursor(first);
        while (cursor != null) {
            MvcResult page = api().home(null, cursor);
            seen.addAll(ids(page));
            cursor = nextCursor(page);
        }

        assertThat(seen).doesNotHaveDuplicates().doesNotContain(fresh, hiddenLater);
        List<Long> expected = new ArrayList<>(fixture.idsOf(PostReadingFixture.HOME_TITLES));
        expected.remove(hiddenLater);
        assertThat(seen).containsExactlyElementsOf(expected);

        // 커서 없이 새로 받으면 새 글이 맨 위
        assertThat(ids(api().home(null, null))).startsWith(fresh);
    }

    @Test
    void 빈_items_응답을_받는_요청이_없다() throws Exception {
        String cursor = null;
        int pages = 0;
        do {
            MvcResult page = api().home(null, cursor);
            assertThat(titles(page)).as("빈 페이지").isNotEmpty();
            cursor = nextCursor(page);
            pages++;
        } while (cursor != null);
        assertThat(pages).isEqualTo(3);
    }

    @Test
    void 요청_한_번에_SQL_한_번() throws Exception {
        try (SqlCounter.Scope scope = SqlCounter.start()) {
            MvcResult result = api().home(null, null);
            assertThat(status(result)).isEqualTo(200);
            assertThat(scope.count()).isEqualTo(1);
        }
    }

    @Test
    void 글이_없으면_빈_목록과_null_위치_값() throws Exception {
        jdbc.update("DELETE FROM post_tag");
        jdbc.update("DELETE FROM post_draft");
        jdbc.update("DELETE FROM post");

        MvcResult result = api().home(null, null);

        assertThat(titles(result)).isEmpty();
        assertThat(nextCursor(result)).isNull();
    }
}
