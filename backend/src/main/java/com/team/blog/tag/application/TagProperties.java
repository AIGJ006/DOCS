package com.team.blog.tag.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * 태그 설정값 ({@code blog.tag.*}, 008 data-model §5, research R16, constitution VII). 기본값은 {@code
 * application.yml}에 둔다. 한 글의 태그 최대 수는 002 {@code blog.post.max-tags}를 그대로 쓰고, 자동완성 0.3초 멈춤은 화면
 * 상수({@code TAG_SUGGEST_DEBOUNCE_MS})다.
 *
 * @param topLimit 전체 태그 목록 개수 (기본 100)
 * @param blogStrip 블로그 태그 줄
 * @param suggest 자동완성
 */
@Validated
@ConfigurationProperties("blog.tag")
public record TagProperties(
        @Min(1) @Max(1000) @DefaultValue("100") int topLimit,
        @Valid @NotNull @DefaultValue BlogStrip blogStrip,
        @Valid @NotNull @DefaultValue Suggest suggest) {

    /**
     * @param limit 블로그 태그 줄 API가 돌려주는 최대 개수 ([태그 더 보기]로 펼칠 수 있는 범위, 기본 100)
     * @param initial 처음 보이는 개수 (화면에 {@code initialVisible}로 내려 줌, 기본 10)
     */
    public record BlogStrip(
            @Min(1) @Max(1000) @DefaultValue("100") int limit,
            @Min(1) @DefaultValue("10") int initial) {

        @AssertTrue(message = "blog.tag.blog-strip.initial은 limit 이하여야 합니다")
        public boolean isInitialWithinLimit() {
            return initial <= limit;
        }
    }

    /**
     * @param limit 후보 최대 개수 (기본 10)
     * @param rateLimit 회원당 요청 제한 (기본 1분 60번)
     */
    public record Suggest(
            @Min(1) @Max(100) @DefaultValue("10") int limit,
            @Valid @NotNull @DefaultValue RateLimit rateLimit) {}

    /**
     * @param limit 창 안에서 허용하는 횟수
     * @param window 창 길이
     */
    public record RateLimit(
            @Min(1) @DefaultValue("60") int limit, @NotNull @DefaultValue("1m") Duration window) {}
}
