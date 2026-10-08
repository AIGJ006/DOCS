package com.team.blog.tag.integration;

import static com.team.blog.tag.support.TagApi.body;
import static com.team.blog.tag.support.TagApi.bytes;
import static com.team.blog.tag.support.TagApi.ids;
import static com.team.blog.tag.support.TagApi.read;
import static com.team.blog.tag.support.TagApi.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.discovery.application.PostListCursor;
import com.team.blog.shared.web.cursor.ListScope;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import com.team.blog.support.fixture.PostFixtures;
import com.team.blog.tag.support.TagApi;
import com.team.blog.tag.support.TagFixtures;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 태그별 글 목록 API (008 T027, US2 #1~#3, SC-005, FR-022~027). 목록·글 수는 보는 사람과 상관없이 전체 공개 조건(004 {@code
 * VisibilityFilter.forViewer(익명)})이다.
 */
class TagPostListIT extends IntegrationTestBase {

    @Autowired private PostListCursor cursors;

    private TagApi api() {
        return new TagApi(mockMvc);
    }

    private TagFixtures tags() {
        return new TagFixtures(jdbc);
    }

    private PostFixtures posts() {
        return new PostFixtures(jdbc);
    }

    /** 공개 글 n개 (오래된 것부터 1초 간격). 최신순 번호 목록을 돌려준다. */
    private List<Long> publicPosts(long author, int n, String tag) {
        Instant base = Instant.now().minus(Duration.ofDays(2)).truncatedTo(ChronoUnit.MICROS);
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            long id =
                    posts().post(author)
                            .published("PUBLIC")
                            .firstPublicAt(base.plusSeconds(i))
                            .title("공개 " + i)
                            .create();
            tags().attach(id, tag);
            ids.add(0, id);
        }
        return ids;
    }

    /** 공개가 아닌 모든 상태(비공개·임시·휴지통·숨김·탈퇴 신청 작성자)의 글에 태그를 붙인다. */
    private void hiddenStates(String tag) {
        for (PostFixtures.State state :
                List.of(
                        PostFixtures.State.PUBLISHED_PRIVATE,
                        PostFixtures.State.DRAFT,
                        PostFixtures.State.TRASHED,
                        PostFixtures.State.HIDDEN,
                        PostFixtures.State.AUTHOR_WITHDRAWN)) {
            long author = members().member().create();
            tags().attach(posts().create(author, state), tag);
        }
    }

    @Test
    void 공개_글만_최신순_9개와_커서() throws Exception {
        long author = members().member().create();
        List<Long> expected = publicPosts(author, 12, "spring-boot");
        hiddenStates("spring-boot");

        MvcResult first = api().posts(null, "spring-boot", null);

        assertThat(status(first)).as(body(first)).isEqualTo(200);
        assertThat(ids(first)).containsExactlyElementsOf(expected.subList(0, 9));
        assertThat(first.getResponse().getHeader("Cache-Control")).isEqualTo("private, no-cache");
        String cursor = read(first, "$.nextCursor");
        assertThat(cursor).isNotNull();

        MvcResult second = api().posts(null, "spring-boot", cursor);
        assertThat(ids(second)).containsExactlyElementsOf(expected.subList(9, 12));
        assertThat((Object) read(second, "$.nextCursor")).isNull();
        assertThat((String) read(first, "$.items[0].url")).startsWith("/@");
    }

    @Test
    void 머리말_글_수는_공개_글만() throws Exception {
        long author = members().member().create();
        publicPosts(author, 12, "spring-boot");
        hiddenStates("spring-boot");

        MvcResult result = api().summary(null, "spring-boot");

        assertThat(status(result)).as(body(result)).isEqualTo(200);
        assertThat((String) read(result, "$.name")).isEqualTo("spring-boot");
        assertThat((Integer) read(result, "$.postCount")).isEqualTo(12);
        assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("private, no-cache");
    }

    @Test
    void 로그인해도_작성자여도_같은_공개_글만() throws Exception {
        long author = members().member().create();
        publicPosts(author, 2, "jpa");
        long privatePost = posts().create(author, PostFixtures.State.PUBLISHED_PRIVATE);
        tags().attach(privatePost, "jpa");
        Cookie authorSession = TestLogin.loginAs(mockMvc, author);

        assertThat(bytes(api().posts(authorSession, "jpa", null)))
                .isEqualTo(bytes(api().posts(null, "jpa", null)));
        assertThat((Integer) read(api().summary(authorSession, "jpa"), "$.postCount")).isEqualTo(2);
    }

    @Test
    void 비공개_전용_태그와_없는_태그의_응답이_같다() throws Exception {
        // 같은 이름으로 두 상태를 차례로 만들어 비교한다(이름이 응답에 들어가므로)
        String name = "이직준비";
        MvcResult noneSummary = api().summary(null, name);
        MvcResult nonePosts = api().posts(null, name, null);

        long author = members().member().create();
        tags().attach(posts().create(author, PostFixtures.State.PUBLISHED_PRIVATE), name);
        tags().attach(posts().create(author, PostFixtures.State.DRAFT), name);
        assertThat(tags().countByName(name)).isEqualTo(1);
        MvcResult privateSummary = api().summary(null, name);
        MvcResult privatePosts = api().posts(null, name, null);

        assertThat(status(noneSummary)).isEqualTo(200);
        assertThat(status(privateSummary)).isEqualTo(200);
        assertThat(bytes(privateSummary)).isEqualTo(bytes(noneSummary));
        assertThat(body(noneSummary)).isEqualTo("{\"name\":\"이직준비\",\"postCount\":0}");
        assertThat(status(nonePosts)).isEqualTo(200);
        assertThat(status(privatePosts)).isEqualTo(200);
        assertThat(bytes(privatePosts)).isEqualTo(bytes(nonePosts));
        assertThat(body(nonePosts)).isEqualTo("{\"items\":[],\"nextCursor\":null}");
        for (String header : List.of("Cache-Control", "Content-Type")) {
            assertThat(privateSummary.getResponse().getHeader(header))
                    .isEqualTo(noneSummary.getResponse().getHeader(header));
            assertThat(privatePosts.getResponse().getHeader(header))
                    .isEqualTo(nonePosts.getResponse().getHeader(header));
        }
    }

    @Disabled("FRIENDS 규칙 도입 시 — 공용 컨텍스트에는 친구 공개 규칙 Bean이 없다(004 FriendsVisibilityIT는 별도 컨테이너·프로필)")
    @Test
    void 친구_공개_규칙이어도_전체_공개만() {}

    @Test
    void 정규화되지_않은_이름은_404() throws Exception {
        long author = members().member().create();
        publicPosts(author, 1, "spring-boot");

        for (String raw :
                List.of(
                        "/api/tags/Spring%20Boot",
                        "/api/tags/SPRING-BOOT", "/api/tags/%23spring-boot")) {
            for (String suffix : List.of("/summary", "/posts")) {
                MvcResult result = api().getRaw(null, raw + suffix);
                assertThat(status(result)).as(raw + suffix).isEqualTo(404);
                assertThat(result.getResponse().getHeader("Location")).isNull();
                assertThat(body(result))
                        .isEqualTo(
                                "{\"code\":\"NOT_FOUND\",\"message\":\"볼 수 없는 페이지예요\","
                                        + "\"errors\":[],\"details\":null}");
            }
        }
    }

    @Test
    void 형식이_틀린_이름은_404() throws Exception {
        for (String raw :
                List.of(
                        "/api/tags/%F0%9F%94%A5",
                        "/api/tags/c%40d",
                        "/api/tags/...",
                        "/api/tags/%E3%85%8B%E3%85%8B",
                        "/api/tags/" + "a".repeat(31))) {
            for (String suffix : List.of("/summary", "/posts")) {
                MvcResult result = api().getRaw(null, raw + suffix);
                assertThat(status(result)).as(raw + suffix).isEqualTo(404);
                assertThat((String) read(result, "$.code")).isEqualTo("NOT_FOUND");
            }
        }
    }

    @Test
    void 다른_목록의_커서는_400() throws Exception {
        long author = members().member().create();
        publicPosts(author, 1, "jpa");
        Instant at = Instant.now();
        for (ListScope other :
                List.of(ListScope.home(), ListScope.blog("kim"), ListScope.tag("spring"))) {
            String foreign = cursors.encode(other, at, 1);
            MvcResult result = api().posts(null, "jpa", foreign);
            assertThat(status(result)).as(other.value()).isEqualTo(400);
            assertThat((String) read(result, "$.code")).isEqualTo("INVALID_CURSOR");
        }
        // 태그가 아직 없어도 커서 검사는 같다
        MvcResult unknown =
                api().posts(null, "nobody-uses", cursors.encode(ListScope.home(), at, 1));
        assertThat(status(unknown)).isEqualTo(400);
        assertThat(
                        status(
                                api().posts(
                                                null,
                                                "nobody-uses",
                                                cursors.encode(
                                                        ListScope.tag("nobody-uses"), at, 1))))
                .isEqualTo(200);
    }

    @Test
    void 허용_문자_태그도_API로_읽힌다() throws Exception {
        long author = members().member().create();
        List<String> names = List.of("c#", "c++", "node.js", ".net", "스프링-부트", "자바_기초");
        for (String name : names) {
            publicPosts(author, 1, name);
        }
        for (String name : names) {
            MvcResult result = api().summary(null, name);
            assertThat(status(result)).as(name + " " + body(result)).isEqualTo(200);
            assertThat((Integer) read(result, "$.postCount")).as(name).isEqualTo(1);
            assertThat(ids(api().posts(null, name, null))).as(name).hasSize(1);
        }
    }
}
