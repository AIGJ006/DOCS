package com.team.blog.interaction.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * 팔로우 설정값 ({@code blog.follow.*}, 010 research R12, constitution VII). 기본값은 {@code
 * application.yml}에 둔다. 피드 카드 수는 005 {@code blog.list.page-size}를 그대로 쓴다.
 *
 * @param rateLimit 회원당 팔로우·언팔로우를 합친 요청 제한 (기본 1분 30번, Clarifications Q1)
 * @param listPageSize 팔로워·팔로잉 목록 한 번에 보이는 수 (기본 20, 24 §2-2)
 */
@Validated
@ConfigurationProperties("blog.follow")
public record FollowProperties(
        @Valid @NotNull @DefaultValue RateLimit rateLimit,
        @Min(1) @Max(100) @DefaultValue("20") int listPageSize) {

    /**
     * @param limit 창 안에서 허용하는 횟수
     * @param window 창 길이
     */
    public record RateLimit(
            @Min(1) @DefaultValue("30") int limit, @NotNull @DefaultValue("1m") Duration window) {}
}
