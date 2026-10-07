package com.team.blog.post.application.exception;

import com.team.blog.post.domain.PostReasonCode;
import com.team.blog.shared.error.ApiException;
import java.util.List;
import java.util.Map;

/**
 * Redis가 메모리 부족({@code OOM})으로 쓰기를 거부함 → 503 {@code AUTOSAVE_UNAVAILABLE} (FR-018: 밀어내지 않고 브라우저가
 * 재시도, research B-5). Redis 장애(연결 실패·시간 초과)와 달리 DB 직접 저장으로 넘기지 않는다. {@code
 * shared.infra.redis.RedisGuard}가 던진다.
 */
public class AutosaveUnavailableException extends ApiException {

    public AutosaveUnavailableException() {
        this(null);
    }

    public AutosaveUnavailableException(Throwable cause) {
        super(
                PostReasonCode.AUTOSAVE_UNAVAILABLE,
                PostReasonCode.AUTOSAVE_UNAVAILABLE.defaultMessage(),
                List.of(),
                null,
                Map.of());
        if (cause != null) {
            initCause(cause);
        }
    }
}
