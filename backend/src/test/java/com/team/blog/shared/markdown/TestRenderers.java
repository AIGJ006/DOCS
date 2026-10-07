package com.team.blog.shared.markdown;

import com.team.blog.post.support.StubImageReferenceResolver;
import com.team.blog.shared.application.markdown.MarkdownProperties;
import com.team.blog.shared.infra.markdown.AstTransformer;
import com.team.blog.shared.infra.markdown.CommonmarkFactory;
import com.team.blog.shared.infra.markdown.DefaultContentRenderer;
import com.team.blog.shared.infra.markdown.ExcerptExtractor;
import com.team.blog.shared.infra.markdown.SanitizerPolicy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 렌더러 단위 테스트용 조립 (Spring 없이 실제 부품 그대로). */
final class TestRenderers {

    /** 테스트에서 글 작성자로 쓰는 회원 번호. */
    static final long AUTHOR = 1L;

    /** 남(다른 회원)의 번호. */
    static final long OTHER = 2L;

    /** {@link #AUTHOR}가 올린 사진 (썸네일 있음). */
    static final String OWNED_KEY = "images/2026/10/0b6e1f5c-2a3d-4f7e-9c1a-5d8e7f6a1b2c.webp";

    static final String OWNED_THUMB_KEY =
            "images/2026/10/0b6e1f5c-2a3d-4f7e-9c1a-5d8e7f6a1b2c_thumb.webp";

    /** {@link #AUTHOR}가 올린 옛 사진 (썸네일 없음). */
    static final String OWNED_NO_THUMB_KEY =
            "images/2025/01/11111111-2222-4333-8444-555555555555.png";

    /** {@link #OTHER}가 올린 사진. */
    static final String OTHERS_KEY = "images/2026/09/99999999-8888-4777-8666-555555555555.jpg";

    static final ExecutorService EXECUTOR =
            Executors.newFixedThreadPool(
                    4,
                    r -> {
                        Thread t = new Thread(r, "test-render");
                        t.setDaemon(true);
                        return t;
                    });

    private TestRenderers() {}

    static StubImageReferenceResolver resolver() {
        return new StubImageReferenceResolver()
                .own(AUTHOR, OWNED_KEY, OWNED_THUMB_KEY)
                .own(AUTHOR, OWNED_NO_THUMB_KEY, null)
                .own(OTHER, OTHERS_KEY, null);
    }

    static DefaultContentRenderer create() {
        return create(resolver(), MarkdownProperties.defaults());
    }

    static DefaultContentRenderer create(
            StubImageReferenceResolver resolver, MarkdownProperties properties) {
        return create(resolver, properties, new AstTransformer(resolver, properties));
    }

    static DefaultContentRenderer create(
            StubImageReferenceResolver resolver,
            MarkdownProperties properties,
            AstTransformer transformer) {
        return new DefaultContentRenderer(
                new CommonmarkFactory(),
                transformer,
                SanitizerPolicy.of(StubImageReferenceResolver.PUBLIC_BASE_URL),
                new ExcerptExtractor(),
                resolver,
                EXECUTOR,
                properties);
    }

    static String url(String key) {
        return StubImageReferenceResolver.PUBLIC_BASE_URL + "/" + key;
    }

    static String legacyUrl(String key) {
        return StubImageReferenceResolver.LEGACY_BASE_URL + "/" + key;
    }
}
