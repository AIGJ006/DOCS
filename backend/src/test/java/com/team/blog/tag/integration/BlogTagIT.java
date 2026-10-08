package com.team.blog.tag.integration;

import static com.team.blog.tag.support.TagApi.body;
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
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 블로그 태그 줄과 태그 필터 (008 T054, US5 #1·#2·#4). 조건은 블로그 목록과 같은 {@code forViewer(viewer, ownerId)} — 주인이
 * 봐도 자기 비공개 글의 태그는 없다(06 V-8).
 */
class BlogTagIT extends IntegrationTestBase {

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

    private String handleOf(long memberId) {
        return jdbc.queryForObject(
                "SELECT handle FROM member WHERE id = ?", String.class, memberId);
    }

    private long publicPost(long author, Instant at, String... tagNames) {
        long id = posts().post(author).published("PUBLIC").firstPublicAt(at).create();
        tags().attach(id, tagNames);
        return id;
    }

    @Test
    void 글_수_많은_순으로_태그와_수() throws Exception {
        long owner = members().member().create();
        Instant base = Instant.now().minus(Duration.ofDays(1)).truncatedTo(ChronoUnit.MICROS);
        for (int i = 0; i < 3; i++) {
            publicPost(owner, base.plusSeconds(i), "jpa", "spring");
        }
        publicPost(owner, base.plusSeconds(10), "spring");
        for (int i = 0; i < 100; i++) {
            publicPost(owner, base.plusSeconds(20 + i), String.format("t-%03d", i));
        }
        // 다른 블로그의 태그는 섞이지 않는다
        long other = members().member().create();
        publicPost(other, base, "other-blog");

        MvcResult result = api().blogTags(null, handleOf(owner));

        assertThat(status(result)).as(body(result)).isEqualTo(200);
        assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("private, no-cache");
        assertThat(TagApi.<Integer>read(result, "$.initialVisible")).isEqualTo(10);
        List<Map<String, Object>> items = read(result, "$.items");
        assertThat(items).hasSize(100);
        assertThat(items.get(0)).containsEntry("name", "spring").containsEntry("postCount", 4);
        assertThat(items.get(1)).containsEntry("name", "jpa").containsEntry("postCount", 3);
        assertThat(items.get(2)).containsEntry("name", "t-000").containsEntry("postCount", 1);
        assertThat(items.get(99)).containsEntry("name", "t-097");
        assertThat(items).noneMatch(i -> "other-blog".equals(i.get("name")));
    }

    @Test
    void 필터는_그_태그_글만_9개와_커서() throws Exception {
        long owner = members().member().create();
        String handle = handleOf(owner);
        Instant base = Instant.now().minus(Duration.ofDays(1)).truncatedTo(ChronoUnit.MICROS);
        List<Long> jpa = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            jpa.add(0, publicPost(owner, base.plusSeconds(i * 2L), "jpa"));
            publicPost(owner, base.plusSeconds(i * 2L + 1), "spring");
        }
        long other = members().member().create();
        publicPost(other, base.plusSeconds(100), "jpa");

        MvcResult first = api().blogPosts(null, handle, "jpa", null);
        assertThat(status(first)).as(body(first)).isEqualTo(200);
        assertThat(ids(first)).containsExactlyElementsOf(jpa.subList(0, 9));
        String next = read(first, "$.nextCursor");
        assertThat(next).isNotNull();

        MvcResult second = api().blogPosts(null, handle, "jpa", next);
        assertThat(ids(second)).containsExactlyElementsOf(jpa.subList(9, 12));
        assertThat((Object) read(second, "$.nextCursor")).isNull();

        // 필터 없는 블로그 커서·태그 페이지 커서·다른 태그 커서는 400
        Instant at = Instant.now();
        for (ListScope foreign :
                List.of(
                        ListScope.blog(handle),
                        ListScope.tag("jpa"),
                        ListScope.blogTag(handle, "spring"))) {
            MvcResult result = api().blogPosts(null, handle, "jpa", cursors.encode(foreign, at, 1));
            assertThat(status(result)).as(foreign.value()).isEqualTo(400);
            assertThat((String) read(result, "$.code")).isEqualTo("INVALID_CURSOR");
        }
        // 필터 커서를 필터 없는 목록에 넣어도 400
        MvcResult reversed = api().blogPosts(null, handle, null, next);
        assertThat(status(reversed)).isEqualTo(400);
    }

    @Test
    void 없는_태그_필터는_빈_목록이고_커서_검사는_같다() throws Exception {
        long owner = members().member().create();
        String handle = handleOf(owner);
        publicPost(owner, Instant.now(), "jpa");

        MvcResult empty = api().blogPosts(null, handle, "nobody-uses", null);
        assertThat(status(empty)).isEqualTo(200);
        assertThat(ids(empty)).isEmpty();

        MvcResult foreign =
                api().blogPosts(
                                null,
                                handle,
                                "nobody-uses",
                                cursors.encode(ListScope.blog(handle), Instant.now(), 1));
        assertThat(status(foreign)).isEqualTo(400);
    }

    @Test
    void 비공개_전용_태그는_줄에_없다() throws Exception {
        long owner = members().member().create();
        publicPost(owner, Instant.now(), "java");
        for (PostFixtures.State state :
                List.of(
                        PostFixtures.State.PUBLISHED_PRIVATE,
                        PostFixtures.State.DRAFT,
                        PostFixtures.State.TRASHED,
                        PostFixtures.State.HIDDEN)) {
            tags().attach(posts().create(owner, state), "only-" + state.name().toLowerCase());
        }

        MvcResult asOwner = api().blogTags(TestLogin.loginAs(mockMvc, owner), handleOf(owner));
        assertThat(TagApi.<List<String>>read(asOwner, "$.items[*].name")).containsExactly("java");

        MvcResult filtered =
                api().blogPosts(
                                TestLogin.loginAs(mockMvc, owner),
                                handleOf(owner),
                                "only-published_private",
                                null);
        assertThat(status(filtered)).isEqualTo(200);
        assertThat(ids(filtered)).isEmpty();
    }

    @Test
    void 없는_블로그는_404() throws Exception {
        long withdrawn = members().member().create();
        posts().create(withdrawn, PostFixtures.State.AUTHOR_WITHDRAWN);
        long upper = members().member().create();

        for (String handle :
                List.of("nobody-here", handleOf(withdrawn), handleOf(upper).toUpperCase())) {
            MvcResult tagsResult = api().blogTags(null, handle);
            assertThat(status(tagsResult)).as(handle).isEqualTo(404);
            assertThat(body(tagsResult))
                    .isEqualTo(
                            "{\"code\":\"NOT_FOUND\",\"message\":\"볼 수 없는 페이지예요\",\"errors\":[],\"details\":null}");
            assertThat(status(api().blogPosts(null, handle, "jpa", null)))
                    .as(handle)
                    .isEqualTo(404);
        }
    }

    @Test
    void API의_정규화되지_않은_tag는_404() throws Exception {
        long owner = members().member().create();
        String handle = handleOf(owner);
        publicPost(owner, Instant.now(), "jpa", "spring-boot");

        for (String tag :
                List.of(
                        "JPA",
                        "#jpa",
                        "Spring Boot",
                        "",
                        new String(Character.toChars(0x1F525)),
                        "a".repeat(31))) {
            MvcResult result = api().blogPosts(null, handle, tag, null);
            assertThat(status(result)).as("tag=[%s]", tag).isEqualTo(404);
            assertThat((String) read(result, "$.code")).isEqualTo("NOT_FOUND");
        }
        assertThat(status(api().blogPosts(null, handle, "spring-boot", null))).isEqualTo(200);
    }
}
