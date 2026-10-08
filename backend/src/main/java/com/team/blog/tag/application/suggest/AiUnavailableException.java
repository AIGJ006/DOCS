package com.team.blog.tag.application.suggest;

import com.team.blog.shared.error.ApiException;
import java.util.List;
import java.util.Map;

/**
 * 503 {@code AI_UNAVAILABLE} + {@code details {reason}} (013 data-model §5). 원인 예외는 붙이지 않는다 — 공통
 * 처리기가 5xx를 원인과 함께 로그에 남기는데, 외부 응답 본문·입력이 원인 메시지에 들어 있을 수 있다(R11).
 */
public class AiUnavailableException extends ApiException {

    static final String BUSY_MESSAGE = "잠시 후 다시 시도해 주세요";

    private final AiUnavailableReason reason;

    public AiUnavailableException(AiUnavailableReason reason) {
        super(
                TagSuggestReasonCode.AI_UNAVAILABLE,
                reason == AiUnavailableReason.BUSY
                        ? BUSY_MESSAGE
                        : TagSuggestReasonCode.AI_UNAVAILABLE.defaultMessage(),
                List.of(),
                Map.of("reason", reason.name()),
                Map.of());
        this.reason = reason;
    }

    public AiUnavailableReason reason() {
        return reason;
    }
}
