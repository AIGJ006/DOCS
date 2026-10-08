package com.team.blog.category.integration;

import static com.team.blog.category.support.CategoryApi.body;
import static com.team.blog.category.support.CategoryApi.read;
import static com.team.blog.category.support.CategoryApi.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.team.blog.category.support.CategoryApi;
import com.team.blog.category.support.CategoryFixtures;
import com.team.blog.post.application.PostDraftQueryService;
import com.team.blog.post.application.port.AuthorFollowStatusQuery;
import com.team.blog.post.application.port.PostCategoryPathQuery;
import com.team.blog.post.application.port.PostLikeStatusQuery;
import com.team.blog.post.application.port.PostTagNamesQuery;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.fixture.PostFixtures;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;

/** 글 상세 카테고리 경로 (017 US4 #1~#4, FR-040). */
class PostDetailCategoryIT extends IntegrationTestBase {

    // 005 PostDetailFallbackIntegrationTest·PostDetailAuthorViewIntegrationTest와 같은 덮어쓰기 묶음으로 둬
    // 테스트 컨텍스트를 함께 쓴다(컨텍스트가 늘면 DB 연결이 모자란다). 태그·좋아요·팔로우는 여기서 쓰지 않는다.
    @MockitoBean PostTagNamesQuery tagNames;
    @MockitoBean PostLikeStatusQuery likeStatus;
    @MockitoBean AuthorFollowStatusQuery followStatus;
    @MockitoSpyBean PostDraftQueryService draftQuery;
    @MockitoSpyBean PostCategoryPathQuery categoryPaths;

    private MvcResult detail(long postId) throws Exception {
        return new CategoryApi(mockMvc).getRaw(null, "/api/posts/" + postId);
    }

    @Test
    void US4_1_2_3_하위_최상위_분류_없음() throws Exception {
        CategoryFixtures categories = new CategoryFixtures(jdbc);
        PostFixtures posts = new PostFixtures(jdbc);
        long owner = members().member().create();
        long dev = categories.create(owner, null, "개발");
        long spring = categories.create(owner, dev, "Spring");
        long inChild = posts.create(owner, PostFixtures.State.PUBLISHED_PUBLIC);
        long inTop = posts.create(owner, PostFixtures.State.PUBLISHED_PUBLIC);
        long none = posts.create(owner, PostFixtures.State.PUBLISHED_PUBLIC);
        categories.assign(inChild, spring);
        categories.assign(inTop, dev);

        MvcResult child = detail(inChild);
        assertThat(status(child)).as(body(child)).isEqualTo(200);
        Map<String, Object> path = read(child, "$.category");
        assertThat(path).containsEntry("id", (int) spring).containsEntry("name", "Spring");
        assertThat(read(child, "$.category.parent").toString()).contains("개발");
        assertThat(((Number) read(child, "$.category.parent.id")).longValue()).isEqualTo(dev);

        MvcResult top = detail(inTop);
        assertThat((String) read(top, "$.category.name")).isEqualTo("개발");
        assertThat((Object) read(top, "$.category.parent")).isNull();

        assertThat((Object) read(detail(none), "$.category")).isNull();
    }

    @Test
    void US4_4_조회가_실패해도_상세는_보인다() throws Exception {
        long owner = members().member().create();
        long post = new PostFixtures(jdbc).create(owner, PostFixtures.State.PUBLISHED_PUBLIC);
        CategoryFixtures categories = new CategoryFixtures(jdbc);
        categories.assign(post, categories.create(owner, null, "개발"));
        when(categoryPaths.pathOf(anyLong())).thenThrow(new IllegalStateException("db down"));

        MvcResult result = detail(post);

        assertThat(status(result)).isEqualTo(200);
        assertThat((Object) read(result, "$.category")).isNull();
    }
}
