package com.team.blog.post.domain;

import com.team.blog.shared.error.ReasonCode;
import org.springframework.http.HttpStatus;

/**
 * post 모듈의 이유 코드 (001 {@code CommonReasonCode}는 고치지 않고 기능 enum으로 더한다 — 001 T018).
 *
 * <p>칸 오류({@code errors[].code})에만 쓰는 코드도 같은 enum에 두며, 그때 상태는 쓰이지 않는다(칸 오류를 모은 응답은 400 {@code
 * VALIDATION_FAILED}).
 */
public enum PostReasonCode implements ReasonCode {
    /** 등록되지 않은 공개 범위 값 (004 FR-020, openapi 400 예시). */
    INVALID_VISIBILITY(HttpStatus.BAD_REQUEST, "공개 범위를 다시 선택해 주세요");

    private final HttpStatus status;
    private final String defaultMessage;

    PostReasonCode(HttpStatus status, String defaultMessage) {
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
}
