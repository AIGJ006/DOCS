package com.team.blog.category.integration;

import static com.team.blog.category.support.CategoryApi.body;
import static com.team.blog.category.support.CategoryApi.read;
import static com.team.blog.category.support.CategoryApi.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.category.support.CategoryApi;
import com.team.blog.category.support.CategoryFixtures;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import com.team.blog.support.fixture.PostFixtures;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** 글에 카테고리 고르기 (017 US2 #1~#5, FR-020~FR-022). */
class PostCategoryApiIT extends IntegrationTestBase {

    private CategoryApi api;
    private CategoryFixtures categories;
    private PostFixtures posts;
    private long me;
    private Cookie session;
    private long dev;
    private long spring;

    @BeforeEach
    void setUp() {
        api = new CategoryApi(mockMvc);
        categories = new CategoryFixtures(jdbc);
        posts = new PostFixtures(jdbc);
        me = members().member().create();
        session = TestLogin.loginAs(mockMvc, me);
        dev = categories.create(me, null, "개발");
        spring = categories.create(me, dev, "Spring");
    }

    @Test
    void US2_1_임시글에_고르면_바로_저장되고_다시_읽힌다() throws Exception {
        long draft = posts.create(me, PostFixtures.State.DRAFT);

        MvcResult result = api.setPostCategory(session, draft, spring);

        assertThat(status(result)).as(body(result)).isEqualTo(200);
        assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("private, no-store");
        assertThat(((Number) read(result, "$.categoryId")).longValue()).isEqualTo(spring);
        assertThat(categories.categoryOf(draft)).isEqualTo(spring);
        MvcResult read = api.getPostCategory(session, draft);
        assertThat(((Number) read(read, "$.categoryId")).longValue()).isEqualTo(spring);
        // 최상위도 고를 수 있다
        assertThat(status(api.setPostCategory(session, draft, dev))).isEqualTo(200);
        assertThat(categories.categoryOf(draft)).isEqualTo(dev);
    }

    @Test
    void US2_2_발행_글도_바로_바뀌고_수정됨이_붙지_않는다() throws Exception {
        long post = posts.create(me, PostFixtures.State.PUBLISHED_PUBLIC);
        Map<String, Object> before =
                jdbc.queryForMap(
                        "SELECT edit_version, edited_at, updated_at FROM post WHERE id = ?", post);

        assertThat(status(api.setPostCategory(session, post, spring))).isEqualTo(200);

        Map<String, Object> after =
                jdbc.queryForMap(
                        "SELECT edit_version, edited_at, updated_at FROM post WHERE id = ?", post);
        assertThat(after).isEqualTo(before);
        assertThat(categories.categoryOf(post)).isEqualTo(spring);
    }

    @Test
    void US2_3_분류_없음() throws Exception {
        long post = posts.create(me, PostFixtures.State.PUBLISHED_PUBLIC);
        categories.assign(post, spring);

        MvcResult result = api.setPostCategory(session, post, null);

        assertThat(status(result)).isEqualTo(200);
        assertThat((Object) read(result, "$.categoryId")).isNull();
        assertThat(categories.categoryOf(post)).isNull();
        assertThat((Object) read(api.getPostCategory(session, post), "$.categoryId")).isNull();
    }

    @Test
    void US2_4_남의_카테고리와_없는_번호는_400() throws Exception {
        long post = posts.create(me, PostFixtures.State.DRAFT);
        long others = categories.create(members().member().create(), null, "남의 것");

        for (Long id : List.of(others, 999_999L)) {
            MvcResult result = api.setPostCategory(session, post, id);
            assertThat(status(result)).as(String.valueOf(id)).isEqualTo(400);
            assertThat((String) read(result, "$.code")).isEqualTo("INVALID_CATEGORY");
            assertThat((String) read(result, "$.errors[0].field")).isEqualTo("categoryId");
        }
        assertThat(categories.categoryOf(post)).isNull();
    }

    @Test
    void US2_5_남의_글_휴지통_없는_글은_404_비회원_401_인증_전_403() throws Exception {
        long others =
                posts.create(members().member().create(), PostFixtures.State.PUBLISHED_PUBLIC);
        long trashed = posts.create(me, PostFixtures.State.TRASHED);

        String expected = null;
        for (Object id : List.of(others, trashed, posts.nonexistentId(), "abc")) {
            MvcResult put = api.setPostCategory(session, id, spring);
            MvcResult get = api.getPostCategory(session, id);
            assertThat(status(put)).as("put " + id).isEqualTo(404);
            assertThat(status(get)).as("get " + id).isEqualTo(404);
            if (expected == null) {
                expected = body(put);
            }
            assertThat(body(put)).isEqualTo(expected);
        }
        // 남의 글에 남의 카테고리를 보내도 404가 먼저다
        assertThat(status(api.setPostCategory(session, others, 999_999L))).isEqualTo(404);
        assertThat(categories.categoryOf(others)).isNull();

        long mine = posts.create(me, PostFixtures.State.DRAFT);
        assertThat(status(api.setPostCategory(null, mine, spring))).isEqualTo(401);
        assertThat(status(api.getPostCategory(null, mine))).isEqualTo(401);

        long unverified = members().member().emailVerified(false).create();
        long theirs = posts.create(unverified, PostFixtures.State.DRAFT);
        MvcResult forbidden =
                api.setPostCategory(TestLogin.loginAs(mockMvc, unverified), theirs, null);
        assertThat(status(forbidden)).isEqualTo(403);
        assertThat((String) read(forbidden, "$.code")).isEqualTo("EMAIL_NOT_VERIFIED");
    }

    @Test
    void 휴지통_글의_카테고리는_남고_복원하면_그대로() throws Exception {
        long post = posts.create(me, PostFixtures.State.PUBLISHED_PUBLIC);
        assertThat(status(api.setPostCategory(session, post, spring))).isEqualTo(200);
        jdbc.update("UPDATE post SET deleted_at = now() WHERE id = ?", post);
        assertThat(categories.categoryOf(post)).isEqualTo(spring);
        jdbc.update("UPDATE post SET deleted_at = NULL WHERE id = ?", post);
        assertThat(((Number) read(api.getPostCategory(session, post), "$.categoryId")).longValue())
                .isEqualTo(spring);
    }
}
