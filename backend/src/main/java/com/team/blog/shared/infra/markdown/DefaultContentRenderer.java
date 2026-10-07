package com.team.blog.shared.infra.markdown;

import com.team.blog.shared.application.markdown.ContentRenderer;
import com.team.blog.shared.application.markdown.ContentTooComplexException;
import com.team.blog.shared.application.markdown.ImageContext;
import com.team.blog.shared.application.markdown.ImageReferenceResolver;
import com.team.blog.shared.application.markdown.MarkdownProperties;
import com.team.blog.shared.application.markdown.OwnedImage;
import com.team.blog.shared.application.markdown.RenderVersion;
import com.team.blog.shared.application.markdown.RenderedContent;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.commonmark.node.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * {@link ContentRenderer} 구현 (12 §2, research A-9·B-7). 파싱 → {@link AstTransformer} → HTML 렌더링 →
 * {@link SanitizerPolicy} → {@link ExcerptExtractor} → 썸네일 결정 전체를 전용 풀 {@code renderExecutor}에서
 * 실행하고 {@code blog.markdown.render-timeout}(1초)까지만 기다린다. 넘으면 작업을 취소(인터럽트)하고 {@link
 * ContentTooComplexException}.
 *
 * <p>썸네일 = 본문 첫 작성자 사진의 {@code thumb_storage_key} 공개 주소, 없으면 원본 키의 공개 주소, 작성자 사진이 없으면 {@code null}
 * (FR-028, A-12).
 */
@Component
public class DefaultContentRenderer implements ContentRenderer {

    private static final Logger log = LoggerFactory.getLogger(DefaultContentRenderer.class);

    private final CommonmarkFactory commonmark;
    private final AstTransformer transformer;
    private final SanitizerPolicy sanitizer;
    private final ExcerptExtractor excerpts;
    private final ImageReferenceResolver images;
    private final ExecutorService executor;
    private final Duration timeout;

    public DefaultContentRenderer(
            CommonmarkFactory commonmark,
            AstTransformer transformer,
            SanitizerPolicy sanitizer,
            ExcerptExtractor excerpts,
            ImageReferenceResolver images,
            @Qualifier(RenderExecutorConfig.RENDER_EXECUTOR) ExecutorService executor,
            MarkdownProperties properties) {
        this.commonmark = commonmark;
        this.transformer = transformer;
        this.sanitizer = sanitizer;
        this.excerpts = excerpts;
        this.images = images;
        this.executor = executor;
        this.timeout = properties.renderTimeout();
    }

    @Override
    public RenderedContent render(String contentMd, ImageContext ctx) {
        String source = contentMd == null ? "" : contentMd;
        Future<RenderedContent> future = executor.submit(() -> renderNow(source, ctx));
        try {
            return future.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.info("본문 렌더링 시간 초과: {}자, 제한 {}", source.length(), timeout);
            throw new ContentTooComplexException(e);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new ContentTooComplexException(e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof StackOverflowError) {
                log.info("본문 렌더링 중첩이 너무 깊음: {}자", source.length());
                throw new ContentTooComplexException(cause);
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("본문 렌더링 실패", cause);
        }
    }

    private RenderedContent renderNow(String source, ImageContext ctx) {
        Node document = commonmark.parser().parse(source);
        AstTransformer.Result transformed = transformer.transform(document, ctx.ownerMemberId());
        String html = sanitizer.sanitize(commonmark.renderer().render(document));
        String excerpt = excerpts.extract(document, transformed.imageLinks());
        String thumbnail = transformed.firstOwned().map(this::thumbnailUrl).orElse(null);
        return new RenderedContent(
                html, excerpt, transformed.ownedKeys(), thumbnail, RenderVersion.CURRENT);
    }

    private String thumbnailUrl(OwnedImage image) {
        return images.publicUrlOf(
                image.thumbStorageKey() != null ? image.thumbStorageKey() : image.storageKey());
    }
}
