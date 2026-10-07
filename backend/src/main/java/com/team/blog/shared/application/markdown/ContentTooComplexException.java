package com.team.blog.shared.application.markdown;

import com.team.blog.shared.error.BusinessRuleException;

/**
 * 목록·인용 중첩이 {@code blog.markdown.max-nesting}을 넘거나 렌더링이 {@code blog.markdown.render-timeout}을 넘음 →
 * 400 {@code CONTENT_TOO_COMPLEX} (12 §7-5, FR-046). 미리보기는 그대로 응답하고, 발행은 {@code contentMd} 칸 오류로
 * 모은다.
 */
public class ContentTooComplexException extends BusinessRuleException {

    public ContentTooComplexException() {
        super(MarkdownReasonCode.CONTENT_TOO_COMPLEX);
    }

    public ContentTooComplexException(Throwable cause) {
        this();
        if (cause != null) {
            initCause(cause);
        }
    }
}
