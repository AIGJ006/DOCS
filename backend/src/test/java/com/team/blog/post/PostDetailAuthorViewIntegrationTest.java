package com.team.blog.post;

import static com.team.blog.discovery.support.ReadingApi.body;
import static com.team.blog.discovery.support.ReadingApi.cacheControl;
import static com.team.blog.discovery.support.ReadingApi.read;
import static com.team.blog.discovery.support.ReadingApi.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.jayway.jsonpath.JsonPath;
import com.team.blog.discovery.support.PostReadingFixture;
import com.team.blog.discovery.support.ReadingApi;
import com.team.blog.post.application.PostDraftQueryService;
import com.team.blog.post.application.port.AuthorFollowStatusQuery;
import com.team.blog.post.application.port.PostLikeStatusQuery;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.SqlCounter;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 작성자가 보는 자기 글 상세 {@code GET /api/posts/{postId}} (005 T052, US4 #1~#6, FR-038~040).
 *
 * <p>좋아요·팔로우 포트는 목(기본값 {@code false})으로 바꿔 작성자에게는 부르지 않는지 확인한다. 작업본 조회는 spy로 감싸 실패를 흉내 낸다(research
 * R-30).
 */
class PostDetailAuthorViewIntegrationTest extends IntegrationTestBase {

    @MockitoBean PostLikeStatusQuery likeStatus;
    @MockitoBean AuthorFollowStatusQuery followStatus;
    @MockitoSpyBean PostDraftQueryService draftQuery;

    private PostReadingFixture fixture;
    private Cookie author;

    @BeforeEach
    void load() {
        fixture = PostReadingFixture.load(jdbc);
        author = fixture.loginAs(mockMvc, "A");
    }

    private ReadingApi api() {
        return new ReadingApi(mockMvc);
    }

    @Test
    void 공개_글은_작성자_표시와_빈_authorView이고_좋아요_팔로우를_조회하지_않는다() throws Exception {
        MvcResult result = api().detail(author, fixture.postOf("A", "republished"));

        assertThat(status(result)).as(body(result)).isEqualTo(200);
        assertThat((Boolean) read(result, "$.viewer.loggedIn")).isTrue();
        assertThat((Boolean) read(result, "$.viewer.isAuthor")).isTrue();
        assertThat((Boolean) read(result, "$.viewer.likedByMe")).isFalse();
        assertThat((Boolean) read(result, "$.viewer.followingAuthor")).isFalse();
        assertThat((Boolean) read(result, "$.authorView.hasDraft")).isFalse();
        assertThat((Object) read(result, "$.authorView.draftSavedAt")).isNull();
        assertThat((Boolean) read(result, "$.authorView.hidden")).isFalse();
        assertThat((Object) read(result, "$.authorView.hiddenReason")).isNull();
        assertThat(cacheControl(result)).isEqualTo("private, no-cache");
        verify(likeStatus, never()).isLikedBy(anyLong(), anyLong());
        verify(followStatus, never()).isFollowing(anyLong(), anyLong());
    }

    @Test
    void 수정_중인_글은_마지막_발행본과_작업본_저장_시각() throws Exception {
        long postId = fixture.postOf("A", "editing");

        MvcResult mine = api().detail(author, postId);

        assertThat(status(mine)).as(body(mine)).isEqualTo(200);
        assertThat((String) read(mine, "$.contentHtml"))
                .isEqualTo(
                        jdbc.queryForObject(
                                "SELECT content_html FROM post WHERE id = ?", String.class, postId))
                .isEqualTo("<p>마지막 발행본</p>");
        assertThat(body(mine)).doesNotContain("고치는 중인");
        assertThat((Boolean) read(mine, "$.authorView.hasDraft")).isTrue();
        assertThat((String) read(mine, "$.authorView.draftSavedAt"))
                .isEqualTo("2026-10-03T05:03:00Z");

        // 같은 글을 독자가 보면 같은 본문이고 작성자 정보는 없다 (FR-040)
        MvcResult reader = api().detail(fixture.loginAs(mockMvc, "B"), postId);
        assertThat((String) read(reader, "$.contentHtml")).isEqualTo("<p>마지막 발행본</p>");
        assertThat((Object) read(reader, "$.authorView")).isNull();
    }

    @Test
    void 비공개_글은_발행_일자를_보이고_저장되지_않는다() throws Exception {
        long postId = fixture.postOf("A", "private1");

        MvcResult result = api().detail(author, postId);

        assertThat(status(result)).as(body(result)).isEqualTo(200);
        assertThat((String) read(result, "$.visibility")).isEqualTo("PRIVATE");
        assertThat((String) read(result, "$.displayedAt"))
                .isEqualTo(read(result, "$.publishedAt"))
                .isEqualTo("2026-09-30T10:00:00Z");
        assertThat((Object) read(result, "$.firstPublicAt")).isNull();
        assertThat(cacheControl(result)).isEqualTo("private, no-store");
    }

    @Test
    void 숨겨진_글은_작성자에게_숨김_안내_정보와_함께_보인다() throws Exception {
        MvcResult result = api().detail(author, fixture.postOf("A", "hidden"));

        assertThat(status(result)).as(body(result)).isEqualTo(200);
        assertThat((Boolean) read(result, "$.authorView.hidden")).isTrue();
        assertThat((String) read(result, "$.authorView.hiddenReason")).isEqualTo("SPAM");
        assertThat(cacheControl(result)).isEqualTo("private, no-store");
    }

    @Test
    void 임시글은_에디터_주소만_준다() throws Exception {
        long postId = fixture.postOf("A", "draft");

        MvcResult result = api().detail(author, postId);

        assertThat(status(result)).as(body(result)).isEqualTo(200);
        Map<String, Object> json = JsonPath.read(body(result), "$");
        assertThat(json)
                .containsOnlyKeys("id", "status", "editorPath")
                .containsEntry("status", "DRAFT")
                .containsEntry("editorPath", "/write/" + postId);
        assertThat(((Number) json.get("id")).longValue()).isEqualTo(postId);
        assertThat(cacheControl(result)).isEqualTo("private, no-store");
    }

    @Test
    void 휴지통_글은_작성자에게도_독자와_같은_404() throws Exception {
        MvcResult mine = api().detail(author, fixture.postOf("A", "trashed"));
        MvcResult missing = api().detail(author, 99_999_999L);

        assertThat(status(mine)).isEqualTo(404);
        assertThat(body(mine)).isEqualTo(body(missing));
        assertThat(cacheControl(mine)).isEqualTo("private, no-store");
    }

    @Test
    void 작업본_조회가_실패해도_상세는_보이고_hasDraft는_false() throws Exception {
        doThrow(new IllegalStateException("작업본 저장소 장애")).when(draftQuery).findSavedAt(anyLong());

        MvcResult result = api().detail(author, fixture.postOf("A", "editing"));

        assertThat(status(result)).as(body(result)).isEqualTo(200);
        assertThat((String) read(result, "$.contentHtml")).isEqualTo("<p>마지막 발행본</p>");
        assertThat((Boolean) read(result, "$.authorView.hasDraft")).isFalse();
        assertThat((Object) read(result, "$.authorView.draftSavedAt")).isNull();
    }

    @Test
    void 작성자_상세는_SQL_5번_이하() throws Exception {
        long postId = fixture.postOf("A", "editing");

        try (SqlCounter.Scope scope = SqlCounter.start()) {
            assertThat(status(api().detail(author, postId))).isEqualTo(200);
            assertThat(scope.count()).as("계정 상태(001 필터) + 글+작성자 + 태그 + 작업본").isLessThanOrEqualTo(5);
        }
    }
}
