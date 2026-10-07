package com.team.blog.post.application.exception;

import com.team.blog.post.domain.PostReasonCode;
import com.team.blog.shared.error.TooManyRequestsException;

/** 자동 저장·미리보기 요청 과다 → 429 {@code RATE_LIMITED} + {@code Retry-After}(초, 1 이상) (research B-2). */
public class RateLimitedException extends TooManyRequestsException {

    public RateLimitedException(long retryAfterSeconds) {
        super(PostReasonCode.RATE_LIMITED, retryAfterSeconds);
    }
}
