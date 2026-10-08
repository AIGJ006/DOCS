package com.team.blog.post.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.shared.application.markdown.RenderVersion;
import com.team.blog.shared.application.markdown.RenderedContent;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@code Post.changeVisibility} (004 T026, data-model §4-2 표 전 행, 06 §4·V-6, 05 P-3). 공개 범위 변경은
 * {@code edited_at}·{@code edit_version}·{@code published_at}·{@code hidden_at}을 건드리지 않고, {@code
 * first_public_at}은 처음 "발행 + 전체 공개"가 될 때만 채운다.
 */
class PostChangeVisibilityTest {

    private static final Instant T0 = Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant T1 = T0.plus(Duration.ofDays(1));
    private static final Instant T2 = T0.plus(Duration.ofDays(2));
    private static final Instant T3 = T0.plus(Duration.ofDays(3));
    private static final Instant T4 = T0.plus(Duration.ofDays(4));

    private static Post draft(Visibility visibility) {
        Post post = Post.newDraft(7L, visibility, "제목", "본문", T0);
        ReflectionTestUtils.setField(post, "id", 42L);
        return post;
    }

    /** {@code visibility}로 T1에 발행하고, PUBLIC이면 T1이 최초 공개 일자. 다시 발행(T2)으로 {@code edited_at}을 만든다. */
    private static Post published(Visibility visibility) {
        Post post = draft(visibility);
        post.publish(
                new PublishCommand(42L, 7L, "제목", "본문", List.of(), visibility, 0, "k1"),
                rendered(),
                0,
                T1);
        post.publish(
                new PublishCommand(42L, 7L, "제목", "본문 2", List.of(), visibility, 1, "k2"),
                rendered(),
                1,
                T2);
        return post;
    }

    private static RenderedContent rendered() {
        return new RenderedContent("<p>본문</p>", "요약", List.of(), null, RenderVersion.CURRENT);
    }

    private record Unchanged(
            Instant editedAt, long editVersion, Instant publishedAt, Instant hiddenAt) {
        static Unchanged of(Post post) {
            return new Unchanged(
                    post.editedAt(), post.editVersion(), post.publishedAt(), post.hiddenAt());
        }
    }

    @Test
    void 같은_값이면_아무것도_바꾸지_않는다() {
        Post post = published(Visibility.PUBLIC);
        Instant updatedAt = post.updatedAt();
        Unchanged before = Unchanged.of(post);

        VisibilityChange change = post.changeVisibility(Visibility.PUBLIC, T3);

        assertThat(change.changed()).isFalse();
        assertThat(change.wentPublic()).isFalse();
        assertThat(change.from()).isEqualTo(Visibility.PUBLIC);
        assertThat(post.visibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(post.updatedAt()).isEqualTo(updatedAt);
        assertThat(post.firstPublicAt()).isEqualTo(T1);
        assertThat(Unchanged.of(post)).isEqualTo(before);
    }

    @Test
    void 임시글은_값만_바뀌고_처음_공개가_아니다() {
        Post post = draft(Visibility.PRIVATE);

        VisibilityChange change = post.changeVisibility(Visibility.PUBLIC, T3);

        assertThat(change.changed()).isTrue();
        assertThat(change.wentPublic()).isFalse();
        assertThat(change.from()).isEqualTo(Visibility.PRIVATE);
        assertThat(post.visibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(post.status()).isEqualTo(PostStatus.DRAFT);
        assertThat(post.firstPublicAt()).isNull();
        assertThat(post.publishedAt()).isNull();
        assertThat(post.updatedAt()).isEqualTo(T3);
        assertThat(post.editVersion()).isZero();
    }

    @Test
    void 비공개로_발행한_글을_처음_공개하면_최초_공개_일자가_지금() {
        Post post = published(Visibility.PRIVATE);
        Unchanged before = Unchanged.of(post);
        assertThat(post.firstPublicAt()).isNull();

        VisibilityChange change = post.changeVisibility(Visibility.PUBLIC, T3);

        assertThat(change.changed()).isTrue();
        assertThat(change.wentPublic()).isTrue();
        assertThat(change.from()).isEqualTo(Visibility.PRIVATE);
        assertThat(post.visibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(post.firstPublicAt()).isEqualTo(T3);
        assertThat(post.updatedAt()).isEqualTo(T3);
        assertThat(Unchanged.of(post)).isEqualTo(before);
    }

    @Test
    void 공개였던_글을_비공개로_바꿔도_최초_공개_일자는_그대로() {
        Post post = published(Visibility.PUBLIC);
        Unchanged before = Unchanged.of(post);

        VisibilityChange change = post.changeVisibility(Visibility.PRIVATE, T3);

        assertThat(change.changed()).isTrue();
        assertThat(change.wentPublic()).isFalse();
        assertThat(change.from()).isEqualTo(Visibility.PUBLIC);
        assertThat(post.visibility()).isEqualTo(Visibility.PRIVATE);
        assertThat(post.firstPublicAt()).isEqualTo(T1);
        assertThat(post.updatedAt()).isEqualTo(T3);
        assertThat(post.editedAt()).isEqualTo(T2);
        assertThat(Unchanged.of(post)).isEqualTo(before);
    }

    @Test
    void 공개_비공개_공개로_다시_켜도_최초_공개_일자는_처음_값() {
        Post post = published(Visibility.PUBLIC);
        post.changeVisibility(Visibility.PRIVATE, T3);

        VisibilityChange change = post.changeVisibility(Visibility.PUBLIC, T4);

        assertThat(change.changed()).isTrue();
        assertThat(change.wentPublic()).isFalse();
        assertThat(post.firstPublicAt()).isEqualTo(T1);
        assertThat(post.updatedAt()).isEqualTo(T4);
    }

    @Test
    void 숨긴_글도_바뀌고_숨김은_유지된다() {
        Post post = published(Visibility.PUBLIC);
        Instant hiddenAt = T2.plusSeconds(60);
        ReflectionTestUtils.setField(post, "hiddenAt", hiddenAt);

        VisibilityChange change = post.changeVisibility(Visibility.PRIVATE, T3);

        assertThat(change.changed()).isTrue();
        assertThat(post.visibility()).isEqualTo(Visibility.PRIVATE);
        assertThat(post.hiddenAt()).isEqualTo(hiddenAt);
    }
}
