package com.team.blog.category.integration;

import static com.team.blog.category.support.CategoryApi.body;
import static com.team.blog.category.support.CategoryApi.read;
import static com.team.blog.category.support.CategoryApi.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.category.application.CategoryQueryService;
import com.team.blog.category.support.CategoryApi;
import com.team.blog.category.support.CategoryFixtures;
import com.team.blog.discovery.application.BlogQueryService;
import com.team.blog.discovery.application.PostListCursor;
import com.team.blog.shared.security.Viewer;
import com.team.blog.shared.web.cursor.ListScope;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.SqlCounter;
import com.team.blog.support.TestLogin;
import com.team.blog.support.fixture.PostFixtures;
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

/** 블로그에서 카테고리별로 보기 (017 US3 #1~#7, FR-028~FR-034, SC-003·SC-004). */
class BlogCategoryIT extends IntegrationTestBase {

    @Autowired private PostListCursor cursors;
    @Autowired private CategoryQueryService categoryQueries;
    @Autowired private BlogQueryService blogQueries;

    private CategoryApi api;
    private CategoryFixtures categories;
    private PostFixtures posts;
    private long owner;
    private String handle;
    private long dev;
    private long spring;
    private long react;
    private long life;
    private final Instant base =
            Instant.now().minus(Duration.ofDays(1)).truncatedTo(ChronoUnit.MICROS);
    private int seq;

    @BeforeEach
    void setUp() {
        api = new CategoryApi(mockMvc);
        categories = new CategoryFixtures(jdbc);
        posts = new PostFixtures(jdbc);
        owner = members().member().create();
        handle = categories.handleOf(owner);
        dev = categories.create(owner, null, "개발");
        life = categories.create(owner, null, "일상");
        spring = categories.create(owner, dev, "Spring");
        react = categories.create(owner, dev, "React");
    }

    private long publicIn(Long categoryId) {
        long id =
                posts.post(owner)
                        .published("PUBLIC")
                        .firstPublicAt(base.plusSeconds(seq++))
                        .create();
        categories.assign(id, categoryId);
        return id;
    }

    private List<Long> ids(MvcResult result) {
        List<Number> raw = read(result, "$.items[*].id");
        return raw.stream().map(Number::longValue).toList();
    }

    private MvcResult posts(String category, String cursor) throws Exception {
        String uri = "/api/members/" + handle + "/posts?category=" + category;
        if (cursor != null) {
            uri +=
                    "&cursor="
                            + java.net.URLEncoder.encode(
                                    cursor, java.nio.charset.StandardCharsets.UTF_8);
        }
        return api.getRaw(null, uri);
    }

    @Test
    @SuppressWarnings("unchecked")
    void US3_1_2_글_수는_노출_글만_최상위는_하위_포함() throws Exception {
        publicIn(dev);
        publicIn(spring);
        publicIn(spring);
        publicIn(react);
        publicIn(null);
        for (PostFixtures.State hidden :
                List.of(
                        PostFixtures.State.PUBLISHED_PRIVATE,
                        PostFixtures.State.DRAFT,
                        PostFixtures.State.TRASHED,
                        PostFixtures.State.HIDDEN)) {
            categories.assign(posts.create(owner, hidden), spring);
        }

        for (var session : java.util.Arrays.asList(null, TestLogin.loginAs(mockMvc, owner))) {
            MvcResult result = api.blogCategories(session, handle);
            assertThat(status(result)).as(body(result)).isEqualTo(200);
            assertThat(result.getResponse().getHeader("Cache-Control"))
                    .isEqualTo("private, no-cache");
            assertThat(((Number) read(result, "$.totalCount")).intValue()).isEqualTo(5);
            List<Map<String, Object>> items = read(result, "$.items");
            assertThat(items).extracting(i -> i.get("name")).containsExactly("개발", "일상");
            assertThat(items.get(0)).containsEntry("postCount", 4);
            assertThat(items.get(1)).containsEntry("postCount", 0);
            List<Map<String, Object>> children =
                    (List<Map<String, Object>>) items.get(0).get("children");
            assertThat(children).extracting(c -> c.get("name")).containsExactly("Spring", "React");
            assertThat(children.get(0)).containsEntry("postCount", 2);
            assertThat(children.get(1)).containsEntry("postCount", 1);
        }
    }

    @Test
    void US3_3_4_최상위는_하위_글_포함_하위는_자기_글만_9개와_커서() throws Exception {
        List<Long> devAll = new ArrayList<>();
        List<Long> springOnly = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            long s = publicIn(spring);
            springOnly.add(0, s);
            devAll.add(0, s);
            devAll.add(0, publicIn(i % 2 == 0 ? dev : react));
            publicIn(life);
        }
        long otherOwner = members().member().create();
        long othersPost = posts.create(otherOwner, PostFixtures.State.PUBLISHED_PUBLIC);
        categories.assign(othersPost, categories.create(otherOwner, null, "x"));

        MvcResult first = posts(String.valueOf(dev), null);
        assertThat(status(first)).as(body(first)).isEqualTo(200);
        assertThat(ids(first)).containsExactlyElementsOf(devAll.subList(0, 9));
        String next = read(first, "$.nextCursor");
        MvcResult second = posts(String.valueOf(dev), next);
        assertThat(ids(second)).containsExactlyElementsOf(devAll.subList(9, 12));
        assertThat((Object) read(second, "$.nextCursor")).isNull();

        assertThat(ids(posts(String.valueOf(spring), null))).containsExactlyElementsOf(springOnly);

        // 다른 목록의 커서는 400
        for (ListScope foreign :
                List.of(
                        ListScope.blog(handle),
                        ListScope.blogCategory(handle, spring),
                        ListScope.blogTag(handle, "x"))) {
            MvcResult result =
                    posts(String.valueOf(dev), cursors.encode(foreign, Instant.now(), 1));
            assertThat(status(result)).as(foreign.value()).isEqualTo(400);
            assertThat((String) read(result, "$.code")).isEqualTo("INVALID_CURSOR");
        }
    }

    @Test
    void US3_5_다른_블로그의_카테고리_없는_번호_형식_오류는_404() throws Exception {
        long others = categories.create(members().member().create(), null, "남의 것");
        for (String bad : List.of(String.valueOf(others), "999999", "abc", "0", "-1", "", "1.5")) {
            MvcResult result = posts(bad, null);
            assertThat(status(result)).as(bad).isEqualTo(404);
            assertThat((String) read(result, "$.code")).isEqualTo("NOT_FOUND");
        }
        assertThat(status(api.blogCategories(null, "no-such-blog"))).isEqualTo(404);
    }

    @Test
    void US3_6_7_빈_카테고리와_카테고리_없는_블로그() throws Exception {
        MvcResult empty = posts(String.valueOf(life), null);
        assertThat(status(empty)).isEqualTo(200);
        assertThat(ids(empty)).isEmpty();

        long plain = members().member().create();
        MvcResult none = api.blogCategories(null, categories.handleOf(plain));
        assertThat(status(none)).isEqualTo(200);
        assertThat(((Number) read(none, "$.totalCount")).intValue()).isZero();
        assertThat((List<?>) read(none, "$.items")).isEmpty();
    }

    @Test
    void SC_004_SQL_수는_카테고리_수와_무관() throws Exception {
        publicIn(spring);
        int smallTree = treeSql();
        int smallList = listSql();
        for (int i = 0; i < 20; i++) {
            long top = categories.create(owner, null, "t" + i);
            categories.create(owner, top, "c" + i);
            publicIn(top);
            publicIn(spring);
        }

        assertThat(treeSql())
                .as("카테고리 1번 + 글 수 1번 + 전체 수 1번")
                .isEqualTo(smallTree)
                .isLessThanOrEqualTo(3);
        assertThat(listSql())
                .as("주인 + 카테고리 + 카드 (+ 프로필)")
                .isEqualTo(smallList)
                .isLessThanOrEqualTo(4);
    }

    private int treeSql() {
        try (SqlCounter.Scope scope = SqlCounter.start()) {
            categoryQueries.blogTree(Viewer.anonymous(), owner);
            return scope.count();
        }
    }

    private int listSql() {
        try (SqlCounter.Scope scope = SqlCounter.start()) {
            blogQueries.listCategoryPosts(handle, String.valueOf(dev), null, Viewer.anonymous());
            return scope.count();
        }
    }
}
