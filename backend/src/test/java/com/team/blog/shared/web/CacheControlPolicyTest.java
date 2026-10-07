package com.team.blog.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.post.domain.PostStatus;
import com.team.blog.post.domain.Visibility;
import com.team.blog.post.infra.PostView;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** 캐시 헤더 (research R-12·R-30, FR-015): 공개 글만 {@code no-cache}, 나머지와 404는 {@code no-store}. */
class CacheControlPolicyTest {

    static final String NO_CACHE = "private, no-cache";
    static final String NO_STORE = "private, no-store";
    static final Instant T = Instant.parse("2026-10-01T00:00:00Z");

    @Test
    void 발행된_공개_글은_no_cache() {
        assertThat(CacheControlPolicy.forPost(PostStatus.PUBLISHED, Visibility.PUBLIC, false))
                .isEqualTo(NO_CACHE);
    }

    @Test
    void 비공개_임시_숨긴_글은_no_store() {
        assertThat(CacheControlPolicy.forPost(PostStatus.PUBLISHED, Visibility.PRIVATE, false))
                .isEqualTo(NO_STORE);
        assertThat(CacheControlPolicy.forPost(PostStatus.DRAFT, Visibility.PUBLIC, false))
                .isEqualTo(NO_STORE);
        assertThat(CacheControlPolicy.forPost(PostStatus.DRAFT, Visibility.PRIVATE, false))
                .isEqualTo(NO_STORE);
        assertThat(CacheControlPolicy.forPost(PostStatus.PUBLISHED, Visibility.PUBLIC, true))
                .as("작성자가 보는 숨긴 공개 글")
                .isEqualTo(NO_STORE);
    }

    @Test
    void PostView_오버로드는_숨김_여부를_hidden_at으로_본다() {
        PostView shown =
                new PostView(1L, 2L, PostStatus.PUBLISHED, Visibility.PUBLIC, null, null, null);
        PostView hidden =
                new PostView(1L, 2L, PostStatus.PUBLISHED, Visibility.PUBLIC, null, T, null);
        assertThat(CacheControlPolicy.forPost(shown)).isEqualTo(NO_CACHE);
        assertThat(CacheControlPolicy.forPost(hidden)).isEqualTo(NO_STORE);
    }

    @Test
    void 볼_수_없음_404는_no_store() {
        assertThat(CacheControlPolicy.notFound()).isEqualTo(NO_STORE);
    }
}
