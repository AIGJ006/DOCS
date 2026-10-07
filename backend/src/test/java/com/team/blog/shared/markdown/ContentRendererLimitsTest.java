package com.team.blog.shared.markdown;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team.blog.post.support.StubImageReferenceResolver;
import com.team.blog.shared.application.markdown.ContentTooComplexException;
import com.team.blog.shared.application.markdown.ImageContext;
import com.team.blog.shared.application.markdown.MarkdownProperties;
import com.team.blog.shared.infra.markdown.AstTransformer;
import com.team.blog.shared.infra.markdown.DefaultContentRenderer;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.commonmark.node.Node;
import org.junit.jupiter.api.Test;

/** 렌더링 부하 제한 (12 §7-5·§9-3, FR-046, research A-10·B-7). */
class ContentRendererLimitsTest {

    private static final DefaultContentRenderer RENDERER = TestRenderers.create();
    private static final ImageContext CTX = new ImageContext(TestRenderers.AUTHOR);

    @Test
    void 인용_25단계는_거부한다() {
        assertThatThrownBy(() -> RENDERER.render(">".repeat(25) + " 깊다", CTX))
                .isInstanceOf(ContentTooComplexException.class)
                .hasMessage("글 구조가 너무 복잡해요 (목록·인용은 20단계까지)");
    }

    @Test
    void 목록_25단계는_거부한다() {
        assertThatThrownBy(() -> RENDERER.render(nestedList(25), CTX))
                .isInstanceOf(ContentTooComplexException.class);
    }

    @Test
    void 인용과_목록을_섞어_21단계면_거부하고_20단계는_허용한다() {
        assertThatThrownBy(() -> RENDERER.render(quoted(11, nestedList(10)), CTX))
                .isInstanceOf(ContentTooComplexException.class);
        assertThat(RENDERER.render(quoted(10, nestedList(10)), CTX).html())
                .contains("<blockquote>");
        assertThat(RENDERER.render(nestedList(20), CTX).html()).contains("<ul>");
    }

    @Test
    void 인용_15단계는_허용한다() {
        String html = RENDERER.render(">".repeat(15) + " 괜찮다", CTX).html();
        assertThat(html.split("<blockquote>", -1)).hasSize(16);
        assertThat(html).contains("괜찮다");
    }

    @Test
    void 아주_깊은_인용도_예외로_거부하고_서버가_멈추지_않는다() {
        assertThatThrownBy(() -> RENDERER.render(">".repeat(50_000) + " x", CTX))
                .isInstanceOf(ContentTooComplexException.class);
    }

    @Test
    void 본문_10만_자를_1초_안에_렌더링한다() {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (sb.length() < 100_000) {
            sb.append("## 소제목 ").append(i).append("\n\n");
            sb.append("**굵게** 문단 [링크](https://example.com/").append(i).append(") `코드`\n\n");
            sb.append("- 목록 하나\n  - 둘\n\n> 인용\n\n");
            sb.append("| a | b |\n|---|---|\n| 1 | 2 |\n\n```java\nint x = ").append(i++);
            sb.append(";\n```\n\n");
        }
        String md = sb.substring(0, 100_000);
        long start = System.nanoTime();
        RENDERER.render(md, CTX);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(1));
    }

    @Test
    void 렌더링_시간을_넘기면_거부하고_작업을_취소한다() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        CountDownLatch finished = new CountDownLatch(1);
        MarkdownProperties props =
                new MarkdownProperties(
                        Duration.ofMillis(200),
                        20,
                        4,
                        new MarkdownProperties.RateLimit(60, Duration.ofMinutes(1)),
                        100,
                        Duration.ofMinutes(10));
        StubImageReferenceResolver resolver = TestRenderers.resolver();
        AstTransformer slow =
                new AstTransformer(resolver, props) {
                    @Override
                    public Result transform(Node document, long ownerMemberId) {
                        started.countDown();
                        try {
                            Thread.sleep(5_000);
                        } catch (InterruptedException e) {
                            interrupted.set(true);
                            Thread.currentThread().interrupt();
                        } finally {
                            finished.countDown();
                        }
                        return super.transform(document, ownerMemberId);
                    }
                };
        DefaultContentRenderer renderer = TestRenderers.create(resolver, props, slow);

        long start = System.nanoTime();
        assertThatThrownBy(() -> renderer.render("느린 글", CTX))
                .isInstanceOf(ContentTooComplexException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
        assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(finished.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(interrupted).isTrue();
    }

    /** 모든 줄 앞에 인용 표시 {@code depth}개. */
    private static String quoted(int depth, String text) {
        String prefix = ">".repeat(depth) + " ";
        return prefix + text.strip().replace("\n", "\n" + prefix);
    }

    private static String nestedList(int depth) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            sb.append("  ".repeat(i)).append("- 단계 ").append(i + 1).append('\n');
        }
        return sb.toString();
    }
}
