package com.team.blog.discovery.application.search;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * 검색 설정값 ({@code blog.search.*}, 012 research R17, data-model §7). 기본값은 {@code application.yml}에
 * 둔다. 카드 수는 005 {@code blog.list.page-size}(9)를 함께 쓴다.
 *
 * @param maxLength 검색어 최대 코드 포인트 수 (기본 50, FR-018)
 * @param maxWords 최대 단어 수 (기본 5, FR-018)
 * @param recentWindow 먼저 찾아보는 최근 공개 글 수 (기본 3,000 — 결과 순서와 무관한 성능 설정, 33 §4)
 * @param snippetRadius 주변 문장의 앞뒤 코드 포인트 수 (기본 40, FR-033)
 * @param peopleLimit 사람 검색 최대 결과 수 (기본 20, FR-036)
 * @param rateLimit 글·사람 검색 합산 요청 제한 (기본 1분 30번, FR-037)
 */
@Validated
@ConfigurationProperties("blog.search")
public record SearchProperties(
        @Min(2) @Max(200) @DefaultValue("50") int maxLength,
        @Min(1) @Max(20) @DefaultValue("5") int maxWords,
        @Min(1) @DefaultValue("3000") int recentWindow,
        @Min(1) @Max(200) @DefaultValue("40") int snippetRadius,
        @Min(1) @Max(100) @DefaultValue("20") int peopleLimit,
        @Valid @NotNull @DefaultValue RateLimit rateLimit) {

    /**
     * @param limit 창 안에서 허용하는 횟수
     * @param window 창 길이
     */
    public record RateLimit(
            @Min(1) @DefaultValue("30") int limit, @NotNull @DefaultValue("1m") Duration window) {}
}
