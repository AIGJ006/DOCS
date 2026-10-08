package com.team.blog.post.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team.blog.post.domain.PostStatus;
import com.team.blog.post.domain.TrashablePost;
import com.team.blog.post.domain.Visibility;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** 휴지통 처리용 잠금 조회 값 (006 T003, data-model §3). */
class TrashablePostTest {

    private static TrashablePost post(PostStatus status, Instant deletedAt) {
        return new TrashablePost(1L, 2L, status, Visibility.PUBLIC, "제목", "본문", deletedAt, 3L);
    }

    @Test
    void deletedAt이_없으면_휴지통이_아니다() {
        assertThat(post(PostStatus.PUBLISHED, null).isTrashed()).isFalse();
    }

    @Test
    void deletedAt이_있으면_휴지통이다() {
        assertThat(post(PostStatus.PUBLISHED, Instant.now()).isTrashed()).isTrue();
    }

    @Test
    void purgeAt은_deletedAt에_보관_기간을_더한다_마이크로초_보존() {
        Instant deletedAt = Instant.parse("2026-10-02T05:03:12.123456Z");
        assertThat(post(PostStatus.DRAFT, deletedAt).purgeAt(Duration.ofDays(30)))
                .isEqualTo(Instant.parse("2026-11-01T05:03:12.123456Z"));
    }

    @Test
    void 휴지통이_아니면_purgeAt은_예외() {
        assertThatThrownBy(() -> post(PostStatus.DRAFT, null).purgeAt(Duration.ofDays(30)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void isDraft는_상태가_DRAFT인지() {
        assertThat(post(PostStatus.DRAFT, null).isDraft()).isTrue();
        assertThat(post(PostStatus.PUBLISHED, null).isDraft()).isFalse();
    }
}
