package com.team.blog.shared.error;

import java.util.List;
import java.util.Map;

/**
 * Redis 장애로 보안 확인(새 로그인·토큰 확인·토큰 발급·세션 삭제)을 할 수 없음 → 503 {@code TEMPORARILY_UNAVAILABLE} + {@code
 * Retry-After: 30} (02 §2-1, R-30).
 */
public class TemporarilyUnavailableException extends ApiException {

    public static final long RETRY_AFTER_SECONDS = 30;

    public TemporarilyUnavailableException() {
        this(null);
    }

    public TemporarilyUnavailableException(Throwable cause) {
        super(
                CommonReasonCode.TEMPORARILY_UNAVAILABLE,
                CommonReasonCode.TEMPORARILY_UNAVAILABLE.defaultMessage(),
                List.of(),
                null,
                Map.of("Retry-After", String.valueOf(RETRY_AFTER_SECONDS)));
        if (cause != null) {
            initCause(cause);
        }
    }
}
