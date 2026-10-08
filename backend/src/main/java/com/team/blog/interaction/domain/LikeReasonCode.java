package com.team.blog.interaction.domain;

import com.team.blog.shared.error.ReasonCode;
import org.springframework.http.HttpStatus;

/**
 * 좋아요 거부 이유 (009 data-model §4-2, research R3, FR-009, README "정해진 것" 2026-10-07). 판정 순서상 404(볼 수
 * 없는 글) 뒤, 429(요청 제한) 앞이다. 문구 끝에 마침표를 붙이지 않는다.
 */
public enum LikeReasonCode implements ReasonCode {
    /** 자기 글에 좋아요·취소 (42 P-9 — 30 원문의 403은 42 §4에 따라 400). */
    CANNOT_LIKE_OWN_POST(HttpStatus.BAD_REQUEST, "내 글에는 좋아요를 누를 수 없어요");

    private final HttpStatus status;
    private final String defaultMessage;

    LikeReasonCode(HttpStatus status, String defaultMessage) {
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
