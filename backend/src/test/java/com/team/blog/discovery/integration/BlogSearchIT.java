package com.team.blog.discovery.integration;

import static com.team.blog.discovery.support.SearchApi.body;
import static com.team.blog.discovery.support.SearchApi.ids;
import static com.team.blog.discovery.support.SearchApi.read;
import static com.team.blog.discovery.support.SearchApi.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.discovery.support.SearchApi;
import com.team.blog.discovery.support.SearchFixtures;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import com.team.blog.support.fixture.PostFixtures;
import com.team.blog.support.fixture.PostFixtures.State;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** 블로그 안 검색 (012 T031, US3 #3, FR-031, research R11). */
class BlogSearchIT extends IntegrationTestBase {

    private static final String NOT_FOUND_BODY =
            "{\"code\":\"NOT_FOUND\",\"message\":\"볼 수 없는 페이지예요\",\"errors\":[],\"details\":null}";

    private long a;
    private long b;

    @BeforeEach
    void setUp() {
        a = members().member().handle("blog_a").create();
        b = members().member().handle("blog_b").create();
    }

    private SearchApi api() {
        return new SearchApi(mockMvc);
    }

    private long post(long author, String title, int minutes) {
        return new SearchFixtures(jdbc)
                .post(author)
                .title(title)
                .firstPublicAt(
                        Instant.now().minus(Duration.ofDays(1)).plus(Duration.ofMinutes(minutes)))
                .create();
    }

    @Test
    void US3_3_그_블로그의_공개_글만() throws Exception {
        long mine = post(a, "트랜잭션 정리 A", 0);
        post(b, "트랜잭션 정리 B", 1);
        new SearchFixtures(jdbc)
                .post(a)
                .title("트랜잭션 비공개 A")
                .state(State.PUBLISHED_PRIVATE)
                .create();

        MvcResult result = api().posts("트랜잭션", null, null, "blog_a");

        assertThat(status(result)).as(body(result)).isEqualTo(200);
        assertThat(ids(result)).containsExactly(mine);
        assertThat(ids(api().posts("트랜잭션", null, null, "BLOG_A"))).containsExactly(mine);
    }

    @Test
    void 블로그_주인이_검색해도_공개_글만() throws Exception {
        long visible = post(a, "주인 공개 글", 0);
        new SearchFixtures(jdbc).post(a).title("주인 비공개 글").state(State.PUBLISHED_PRIVATE).create();
        new SearchFixtures(jdbc).post(a).title("주인 숨긴 글").state(State.HIDDEN).create();

        MvcResult result =
                api().as(TestLogin.loginAs(mockMvc, a)).posts("주인", null, null, "blog_a");

        assertThat(ids(result)).containsExactly(visible);
    }

    @Test
    void 없는_주소_유예_익명_처리_블로그는_404_본문_고정() throws Exception {
        long gone = members().member().handle("blog_gone").create();
        post(gone, "트랜잭션 유예", 0);
        new PostFixtures(jdbc).withdraw(gone);
        members().member().handle("blog_anon").deleted().create();

        for (String handle : new String[] {"nobody_here", "blog_gone", "blog_anon"}) {
            MvcResult result = api().posts("트랜잭션", null, null, handle);
            assertThat(status(result)).as(handle).isEqualTo(404);
            assertThat(body(result)).isEqualTo(NOT_FOUND_BODY);
        }
        // 판정 순서: 블로그 404가 검색어 400보다 먼저
        assertThat(status(api().posts("a", null, null, "nobody_here"))).isEqualTo(404);
    }

    @Test
    void 블로그별_커서는_분리된다() throws Exception {
        for (int i = 0; i < 12; i++) {
            post(a, "커서 글 A" + i, i);
            post(b, "커서 글 B" + i, i);
        }
        String aCursor = read(api().posts("커서 글", null, null, "blog_a"), "$.nextCursor");
        String globalCursor = read(api().posts("커서 글"), "$.nextCursor");

        assertThat(status(api().posts("커서 글", null, aCursor, "blog_b"))).isEqualTo(400);
        assertThat(status(api().posts("커서 글", null, globalCursor, "blog_a"))).isEqualTo(400);
        assertThat(status(api().posts("커서 글", null, aCursor, null))).isEqualTo(400);
        assertThat(api().allPostIds("커서 글", null, "blog_a")).hasSize(12);
        assertThat(api().allPostIds("커서 글", "latest", "blog_b")).hasSize(12);
    }
}
