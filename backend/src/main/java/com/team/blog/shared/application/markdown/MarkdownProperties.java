package com.team.blog.shared.application.markdown;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * 본문 렌더러 설정값 ({@code blog.markdown}, 12 §7-5·§7-6·§7-7, 002 research A-10·A-11·B-7).
 *
 * @param renderTimeout 렌더링 한 번의 최대 시간 (기본 1s, 넘으면 400 {@code CONTENT_TOO_COMPLEX})
 * @param maxNesting 목록·인용 최대 중첩 단계 (기본 20)
 * @param renderThreads 전용 렌더링 풀 크기 (기본 4)
 * @param previewRateLimit 사용자당 미리보기 요청 제한 (기본 1분에 60번)
 * @param rerenderBatchSize 다시 렌더링 배치 한 번에 처리할 글 수 (기본 100)
 * @param rerenderInterval 다시 렌더링 배치 확인 주기 (기본 10m)
 */
@Validated
@ConfigurationProperties("blog.markdown")
public record MarkdownProperties(
        @NotNull @DefaultValue("1s") Duration renderTimeout,
        @Min(1) @DefaultValue("20") int maxNesting,
        @Min(1) @DefaultValue("4") int renderThreads,
        @Valid @NotNull @DefaultValue RateLimit previewRateLimit,
        @Min(1) @DefaultValue("100") int rerenderBatchSize,
        @NotNull @DefaultValue("10m") Duration rerenderInterval) {

    /** 렌더러를 설정 없이 만드는 단위 테스트용 기본값. */
    public static MarkdownProperties defaults() {
        return new MarkdownProperties(
                Duration.ofSeconds(1),
                20,
                4,
                new RateLimit(60, Duration.ofMinutes(1)),
                100,
                Duration.ofMinutes(10));
    }

    /**
     * 미리보기 요청 제한 ({@code RateLimiter.tryAcquire("ratelimit:preview:{memberId}", limit, window)}).
     *
     * @param limit 창 안에서 허용하는 횟수 (기본 60)
     * @param window 창 길이 (기본 1m)
     */
    public record RateLimit(
            @Min(1) @DefaultValue("60") int limit, @NotNull @DefaultValue("1m") Duration window) {}
}
