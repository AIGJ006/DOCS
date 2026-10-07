package com.team.blog.shared.error;

import java.util.List;
import java.util.Map;

/** 요청 제한 초과 → 429 + {@code Retry-After}(초). 기본 코드 {@code TOO_MANY_REQUESTS}. */
public class TooManyRequestsException extends ApiException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(long retryAfterSeconds) {
        this(CommonReasonCode.TOO_MANY_REQUESTS, retryAfterSeconds);
    }

    /** 다른 429 이유 코드(예: {@code LOGIN_TEMPORARILY_LOCKED})를 쓸 때. */
    public TooManyRequestsException(ReasonCode reasonCode, long retryAfterSeconds) {
        super(
                reasonCode,
                reasonCode.defaultMessage(),
                List.of(),
                null,
                Map.of("Retry-After", String.valueOf(Math.max(1, retryAfterSeconds))));
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
