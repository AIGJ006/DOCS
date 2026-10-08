package com.team.blog.tag.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.shared.error.ErrorResponse;
import com.team.blog.shared.error.GlobalExceptionHandler;
import com.team.blog.tag.application.suggest.AiConsentRequiredException;
import com.team.blog.tag.application.suggest.AiDailyLimitException;
import com.team.blog.tag.application.suggest.AiUnavailableException;
import com.team.blog.tag.application.suggest.AiUnavailableReason;
import com.team.blog.tag.application.suggest.ContentTooShortException;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

/** 013 이유 코드의 공통 오류 본문 (T008, data-model §5): 상태·code·message(마침표 없음)·errors []·details. */
class TagSuggestErrorBodyTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void 동의_필요는_409와_현재_버전() {
        ResponseEntity<ErrorResponse> r =
                handler.handleApi(new AiConsentRequiredException("2026-10-08"));
        assertThat(r.getStatusCode().value()).isEqualTo(409);
        assertThat(r.getBody().code()).isEqualTo("AI_CONSENT_REQUIRED");
        assertThat(r.getBody().message()).isEqualTo("AI 태그 추천을 쓰려면 동의가 필요해요");
        assertThat(r.getBody().errors()).isEmpty();
        assertThat(r.getBody().details()).isEqualTo(Map.of("version", "2026-10-08"));
    }

    @Test
    void 짧은_글은_422와_minChars_length() {
        ResponseEntity<ErrorResponse> r = handler.handleApi(new ContentTooShortException(100, 42));
        assertThat(r.getStatusCode().value()).isEqualTo(422);
        assertThat(r.getBody().code()).isEqualTo("CONTENT_TOO_SHORT");
        assertThat(r.getBody().message()).isEqualTo("글을 조금 더 쓴 뒤 추천받아 보세요");
        assertThat(r.getBody().details()).isEqualTo(Map.of("minChars", 100, "length", 42));
    }

    @Test
    void 하루_한도는_429와_resetAt_RetryAfter() {
        Instant now = Instant.parse("2026-10-08T14:59:00Z");
        Instant reset = Instant.parse("2026-10-08T15:00:00Z");
        ResponseEntity<ErrorResponse> r = handler.handleApi(new AiDailyLimitException(now, reset));
        assertThat(r.getStatusCode().value()).isEqualTo(429);
        assertThat(r.getBody().code()).isEqualTo("AI_DAILY_LIMIT");
        assertThat(r.getBody().message()).isEqualTo("오늘 추천을 모두 썼어요. 내일 다시 써 보세요");
        assertThat(r.getBody().details()).isEqualTo(Map.of("resetAt", "2026-10-08T15:00:00Z"));
        assertThat(r.getHeaders().getFirst("Retry-After")).isEqualTo("60");
    }

    @Test
    void 추천_불가는_503과_reason_BUSY만_문구가_다르다() {
        for (AiUnavailableReason reason : AiUnavailableReason.values()) {
            ResponseEntity<ErrorResponse> r = handler.handleApi(new AiUnavailableException(reason));
            assertThat(r.getStatusCode().value()).isEqualTo(503);
            assertThat(r.getBody().code()).isEqualTo("AI_UNAVAILABLE");
            assertThat(r.getBody().details()).isEqualTo(Map.of("reason", reason.name()));
            assertThat(r.getBody().message())
                    .isEqualTo(
                            reason == AiUnavailableReason.BUSY
                                    ? "잠시 후 다시 시도해 주세요"
                                    : "지금은 추천할 수 없어요");
        }
    }
}
