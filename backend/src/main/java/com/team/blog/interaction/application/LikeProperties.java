package com.team.blog.interaction.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * 좋아요 설정값 ({@code blog.like.*}, 009 research R14, constitution VII). 기본값은 {@code application.yml}에
 * 둔다.
 *
 * @param rateLimit 회원당 좋아요·취소를 합친 요청 제한 (기본 1분 60번, FR-011)
 * @param reconcileCron 좋아요 수 보정 배치 시각 (기본 매일 04:10, {@code blog.time-zone})
 */
@Validated
@ConfigurationProperties("blog.like")
public record LikeProperties(
        @Valid @NotNull @DefaultValue RateLimit rateLimit,
        @NotBlank @DefaultValue("0 10 4 * * *") String reconcileCron) {

    /**
     * @param limit 창 안에서 허용하는 횟수
     * @param window 창 길이
     */
    public record RateLimit(
            @Min(1) @DefaultValue("60") int limit, @NotNull @DefaultValue("1m") Duration window) {}
}
