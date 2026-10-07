package com.team.blog.shared.application.markdown;

import com.team.blog.shared.error.FieldError;
import com.team.blog.shared.error.ReasonCode;
import org.springframework.http.HttpStatus;

/** 본문 렌더러의 이유 코드 (12 §7-5). 발행 검증에서는 {@code contentMd} 칸 오류 코드로도 쓴다. */
public enum MarkdownReasonCode implements ReasonCode {
    /** 목록·인용 중첩 초과 또는 렌더링 시간 초과. */
    CONTENT_TOO_COMPLEX(HttpStatus.BAD_REQUEST, "글 구조가 너무 복잡해요 (목록·인용은 20단계까지)");

    private final HttpStatus status;
    private final String defaultMessage;

    MarkdownReasonCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }

    /** 이 코드의 칸 오류 한 건. */
    public FieldError fieldError(String field) {
        return new FieldError(field, code(), defaultMessage);
    }
}
