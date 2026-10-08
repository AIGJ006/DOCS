package com.team.blog.post;

import static com.team.blog.discovery.support.ReadingApi.body;
import static com.team.blog.discovery.support.ReadingApi.read;
import static com.team.blog.discovery.support.ReadingApi.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;

import com.team.blog.discovery.support.PostReadingFixture;
import com.team.blog.discovery.support.ReadingApi;
import com.team.blog.post.application.PostDraftQueryService;
import com.team.blog.post.application.port.AuthorFollowStatusQuery;
import com.team.blog.post.application.port.PostLikeStatusQuery;
import com.team.blog.post.application.port.PostTagNamesQuery;
import com.team.blog.support.IntegrationTestBase;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;

/** 부가 정보가 실패해도 상세는 보인다 (005 T026, 원칙 V, research R-30). */
class PostDetailFallbackIntegrationTest extends IntegrationTestBase {

    @MockitoBean PostTagNamesQuery tagNames;
    @MockitoBean PostLikeStatusQuery likeStatus;
    @MockitoBean AuthorFollowStatusQuery followStatus;
    // PostDetailAuthorViewIntegrationTest와 같은 덮어쓰기 묶음으로 둬 테스트 컨텍스트를 함께 쓴다(여기서는 실제 동작 그대로)
    @MockitoSpyBean PostDraftQueryService draftQuery;

    private PostReadingFixture fixture;

    @BeforeEach
    void load() {
        fixture = PostReadingFixture.load(jdbc);
        given(tagNames.namesInOrder(anyLong())).willThrow(new IllegalStateException("태그 모듈 장애"));
        given(likeStatus.isLikedBy(anyLong(), anyLong()))
                .willThrow(new IllegalStateException("좋아요 모듈 장애"));
        given(followStatus.isFollowing(anyLong(), anyLong()))
                .willThrow(new IllegalStateException("팔로우 모듈 장애"));
    }

    private ReadingApi api() {
        return new ReadingApi(mockMvc);
    }

    @Test
    void 태그_좋아요_팔로우_조회가_실패해도_200이고_기본값이다() throws Exception {
        MvcResult result =
                api().detail(fixture.loginAs(mockMvc, "B"), fixture.postOf("A", "republished"));

        assertThat(status(result)).as(body(result)).isEqualTo(200);
        assertThat((List<String>) read(result, "$.tags")).isEmpty();
        assertThat((Boolean) read(result, "$.viewer.likedByMe")).isFalse();
        assertThat((Boolean) read(result, "$.viewer.followingAuthor")).isFalse();
        assertThat((String) read(result, "$.title")).isEqualTo("JPA N+1 정리");
    }
}
