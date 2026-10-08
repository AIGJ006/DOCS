package com.team.blog.tag.application.suggest;

import com.team.blog.shared.error.ReasonCode;
import org.springframework.http.HttpStatus;

/**
 * AI 태그 추천 이유 코드 (013 data-model §5). 문구 끝에 마침표를 붙이지 않는다(README 2026-10-07). 하루 한도는 003 {@code
 * DAILY_UPLOAD_LIMIT}과 같은 규칙으로 별도 코드다(빈도 제한만 {@code TOO_MANY_REQUESTS}, ANALYSIS-tier-bc I1).
 */
public enum TagSuggestReasonCode implements ReasonCode {
    /** 동의 행 없음·버전 다름. {@code details {version}}. */
    AI_CONSENT_REQUIRED(HttpStatus.CONFLICT, "AI 태그 추천을 쓰려면 동의가 필요해요"),
    /** 정리 후 최소 길이 미만. {@code details {minChars, length}}. */
    CONTENT_TOO_SHORT(HttpStatus.UNPROCESSABLE_CONTENT, "글을 조금 더 쓴 뒤 추천받아 보세요"),
    /** 오늘 회원 한도를 다 씀. {@code details {resetAt}} + {@code Retry-After}. */
    AI_DAILY_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "오늘 추천을 모두 썼어요. 내일 다시 써 보세요"),
    /** 추천할 수 없음. {@code details {reason}} ({@link AiUnavailableReason}). */
    AI_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "지금은 추천할 수 없어요");

    private final HttpStatus status;
    private final String defaultMessage;

    TagSuggestReasonCode(HttpStatus status, String defaultMessage) {
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
