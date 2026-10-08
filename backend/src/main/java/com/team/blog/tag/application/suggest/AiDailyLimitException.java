package com.team.blog.tag.application.suggest;

import com.team.blog.shared.error.ApiException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** 429 {@code AI_DAILY_LIMIT} + {@code details {resetAt}} + {@code Retry-After}(그때까지 초, 최소 1). */
public class AiDailyLimitException extends ApiException {

    public AiDailyLimitException(Instant now, Instant resetAt) {
        super(
                TagSuggestReasonCode.AI_DAILY_LIMIT,
                TagSuggestReasonCode.AI_DAILY_LIMIT.defaultMessage(),
                List.of(),
                Map.of("resetAt", resetAt.toString()),
                Map.of(
                        "Retry-After",
                        String.valueOf(Math.max(1, Duration.between(now, resetAt).toSeconds()))));
    }
}
