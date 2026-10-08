package com.team.blog.interaction.domain;

import com.team.blog.shared.error.ReasonCode;
import org.springframework.http.HttpStatus;

/**
 * 팔로우 거부 이유 (010 data-model §5, research R3, FR-003). 판정 순서상 404(볼 수 없는 대상) 뒤, 429(요청 제한) 앞이다. 문구
 * 끝에 마침표를 붙이지 않는다(README "정해진 것").
 */
public enum FollowReasonCode implements ReasonCode {
    /** 자기 자신 팔로우·언팔로우 (24 §3 코드, 문구는 010 제안 — T003). */
    CANNOT_FOLLOW_SELF(HttpStatus.BAD_REQUEST, "자기 자신은 팔로우할 수 없어요");

    private final HttpStatus status;
    private final String defaultMessage;

    FollowReasonCode(HttpStatus status, String defaultMessage) {
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
