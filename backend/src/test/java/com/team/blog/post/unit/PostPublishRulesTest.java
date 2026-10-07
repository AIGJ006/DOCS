package com.team.blog.post.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.post.domain.Post;
import com.team.blog.post.domain.PostStatus;
import com.team.blog.post.domain.PublishCommand;
import com.team.blog.post.domain.PublishResult;
import com.team.blog.post.domain.Visibility;
import com.team.blog.shared.application.markdown.RenderVersion;
import com.team.blog.shared.application.markdown.RenderedContent;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** {@code Post.publish} 시각 규칙 (002 T037, data-model §2 의사 코드, FR-031·032, 20 §3-1). */
class PostPublishRulesTest {

    private static final Instant T0 = Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant T1 = T0.plus(Duration.ofDays(1));
    private static final Instant T2 = T0.plus(Duration.ofDays(2));
    private static final Instant T3 = T0.plus(Duration.ofDays(3));

    private static Post draft() {
        Post post = Post.newDraft(7L, Visibility.PRIVATE, "", "", T0);
        ReflectionTestUtils.setField(post, "id", 42L);
        return post;
    }

    private static PublishCommand cmd(Visibility visibility, long baseVersion) {
        return new PublishCommand(
                42L, 7L, "제목", "본문 **굵게**", List.of("jpa"), visibility, baseVersion, "k");
    }

    private static RenderedContent rendered(String html) {
        return new RenderedContent(
                html, "요약", List.of(), "https://cdn/thumb.webp", RenderVersion.CURRENT);
    }

    @Test
    void 나만_보기로_최초_발행() {
        Post post = draft();
        PublishResult r = post.publish(cmd(Visibility.PRIVATE, 0), rendered("<p>a</p>"), 0, T1);

        assertThat(r.firstPublish()).isTrue();
        assertThat(r.wentPublic()).isFalse();
        assertThat(r.publishedAt()).isEqualTo(T1);
        assertThat(r.firstPublicAt()).isNull();
        assertThat(r.editedAt()).isNull();
        assertThat(r.version()).isEqualTo(1);
        assertThat(post.status()).isEqualTo(PostStatus.PUBLISHED);
        assertThat(post.publishedAt()).isEqualTo(T1);
        assertThat(post.firstPublicAt()).isNull();
        assertThat(post.editedAt()).isNull();
        assertThat(post.visibility()).isEqualTo(Visibility.PRIVATE);
        assertThat(post.title()).isEqualTo("제목");
        assertThat(post.contentMd()).isEqualTo("본문 **굵게**");
        assertThat(post.contentHtml()).isEqualTo("<p>a</p>");
        assertThat(post.excerpt()).isEqualTo("요약");
        assertThat(post.thumbnailUrl()).isEqualTo("https://cdn/thumb.webp");
        assertThat(post.renderVersion()).isEqualTo(RenderVersion.CURRENT);
        assertThat(post.updatedAt()).isEqualTo(T1);
    }

    @Test
    void 나만_보기_발행_뒤_전체_공개로_다시_발행하면_그때가_최초_공개() {
        Post post = draft();
        post.publish(cmd(Visibility.PRIVATE, 0), rendered("<p>a</p>"), 0, T1);

        PublishResult r = post.publish(cmd(Visibility.PUBLIC, 1), rendered("<p>b</p>"), 1, T2);

        assertThat(r.firstPublish()).isFalse();
        assertThat(r.wentPublic()).isTrue();
        assertThat(r.publishedAt()).isEqualTo(T1);
        assertThat(r.firstPublicAt()).isEqualTo(T2);
        assertThat(r.editedAt()).isEqualTo(T2);
        assertThat(r.version()).isEqualTo(2);
    }

    @Test
    void 전체_공개로_최초_발행하면_최초_발행과_최초_공개가_같은_시각() {
        Post post = draft();
        PublishResult r = post.publish(cmd(Visibility.PUBLIC, 0), rendered("<p>a</p>"), 0, T1);
        assertThat(r.firstPublish()).isTrue();
        assertThat(r.wentPublic()).isTrue();
        assertThat(post.publishedAt()).isEqualTo(T1);
        assertThat(post.firstPublicAt()).isEqualTo(T1);
        assertThat(post.editedAt()).isNull();
    }

    @Test
    void 이미_공개된_글을_다시_발행하면_최초_공개는_그대로() {
        Post post = draft();
        post.publish(cmd(Visibility.PUBLIC, 0), rendered("<p>a</p>"), 0, T1);
        post.publish(cmd(Visibility.PRIVATE, 1), rendered("<p>b</p>"), 1, T2);

        PublishResult r = post.publish(cmd(Visibility.PUBLIC, 2), rendered("<p>c</p>"), 2, T3);

        assertThat(r.wentPublic()).isFalse();
        assertThat(r.firstPublicAt()).isEqualTo(T1);
        assertThat(r.publishedAt()).isEqualTo(T1);
        assertThat(r.editedAt()).isEqualTo(T3);
        assertThat(post.contentHtml()).isEqualTo("<p>c</p>");
    }

    @Test
    void 편집_버전은_인자로_받은_현재_버전_더하기_1() {
        Post post = draft();
        // Redis 보관분이 DB보다 앞서 있으면 현재 버전 = Redis 버전 (A-6 ④)
        PublishResult r = post.publish(cmd(Visibility.PUBLIC, 5), rendered("<p>a</p>"), 5, T1);
        assertThat(r.version()).isEqualTo(6);
        assertThat(post.editVersion()).isEqualTo(6);
    }

    @Test
    void 결과에_글_주소를_붙인다() {
        Post post = draft();
        PublishResult r = post.publish(cmd(Visibility.PUBLIC, 0), rendered("<p>a</p>"), 0, T1);
        assertThat(r.url()).isNull();
        assertThat(r.withUrl("/@kim/posts/42").url()).isEqualTo("/@kim/posts/42");
        assertThat(r.withUrl("/@kim/posts/42").version()).isEqualTo(r.version());
    }
}
