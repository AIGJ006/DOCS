package com.team.blog.post.application;

import com.team.blog.post.config.PostAuthoringProperties;
import com.team.blog.post.domain.PostReasonCode;
import com.team.blog.shared.application.markdown.ContentRenderer;
import com.team.blog.shared.application.markdown.ImageContext;
import com.team.blog.shared.application.markdown.MarkdownProperties;
import com.team.blog.shared.error.BusinessRuleException;
import com.team.blog.shared.infra.ratelimit.RateLimiter;
import org.springframework.stereotype.Service;

/**
 * 서버 렌더러 미리보기 (002 T061, FR-047, docs/12 §7-6). 발행과 같은 {@link ContentRenderer}의 정화된 HTML만 돌려준다.
 * 로그인만 확인하고 계정 상태(403)는 보지 않는다(B-12) — 저장하지 않는 읽기 동작이다. 사진 판별 기준은 로그인한 본인.
 */
@Service
public class MarkdownPreviewService {

    private final RateLimiter rateLimiter;
    private final ContentRenderer renderer;
    private final MarkdownProperties markdownProperties;
    private final PostAuthoringProperties properties;

    public MarkdownPreviewService(
            RateLimiter rateLimiter,
            ContentRenderer renderer,
            MarkdownProperties markdownProperties,
            PostAuthoringProperties properties) {
        this.rateLimiter = rateLimiter;
        this.renderer = renderer;
        this.markdownProperties = markdownProperties;
        this.properties = properties;
    }

    /**
     * @throws com.team.blog.shared.error.TooManyRequestsException 사용자당 1분 60번 초과 (429 + {@code
     *     Retry-After})
     * @throws BusinessRuleException 본문 100,000자 초과 (400 {@code CONTENT_TOO_LONG})
     * @throws com.team.blog.shared.application.markdown.ContentTooComplexException 중첩·시간 제한 (400)
     */
    public String preview(long memberId, String contentMd) {
        MarkdownProperties.RateLimit limit = markdownProperties.previewRateLimit();
        rateLimiter.acquireOrThrow("ratelimit:preview:" + memberId, limit.limit(), limit.window());
        String md = contentMd == null ? "" : contentMd;
        if (md.codePointCount(0, md.length()) > properties.post().contentMax()) {
            throw new BusinessRuleException(PostReasonCode.CONTENT_TOO_LONG);
        }
        return renderer.render(md, new ImageContext(memberId)).html();
    }
}
