package com.team.blog.interaction.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * 조회수 설정값 ({@code blog.view.*}, 009 research R14, FR-022·FR-033, constitution VII). 기본값은 {@code
 * application.yml}에 둔다. 중복 기준(기간·횟수)을 바꾸면 화면 안내 문구(005 {@code VIEW_COUNT_NOTICE})도 함께 바꾼다.
 *
 * @param dedupeWindow 같은 방문자·같은 글을 세는 기간 (첫 조회부터, 기본 24시간, 1분 이상)
 * @param maxPerWindow 기간 안에 세는 최대 횟수 (기본 1)
 * @param rateLimit 같은 방문자의 조회 기록 요청 제한 (기본 1분 60번)
 * @param flushInterval 모은 조회를 DB에 반영하는 주기 (기본 1분)
 * @param dailyRetention 일별 조회수 보관 기간 (기본 90일)
 * @param retentionCron 일별 조회수 보관 정리 시각 (기본 매일 04:20, {@code blog.time-zone})
 * @param botUserAgentWords 봇·미리보기 User-Agent 단어 목록 파일 (기본 {@code
 *     classpath:policy/view-bot-user-agents.txt})
 * @param visitorCookieMaxAge 비회원 방문자 쿠키 {@code vid} 유지 기간 (기본 365일)
 */
@Validated
@ConfigurationProperties("blog.view")
public record ViewProperties(
        @NotNull @DefaultValue("24h") Duration dedupeWindow,
        @Min(1) @DefaultValue("1") int maxPerWindow,
        @Valid @NotNull @DefaultValue RateLimit rateLimit,
        @NotNull @DefaultValue("1m") Duration flushInterval,
        @NotNull @DefaultValue("90d") Duration dailyRetention,
        @NotBlank @DefaultValue("0 20 4 * * *") String retentionCron,
        @NotBlank @DefaultValue("classpath:policy/view-bot-user-agents.txt")
                String botUserAgentWords,
        @NotNull @DefaultValue("365d") Duration visitorCookieMaxAge) {

    @AssertTrue(message = "blog.view.dedupe-window는 1분 이상이어야 합니다")
    public boolean isDedupeWindowAtLeastOneMinute() {
        return dedupeWindow == null || dedupeWindow.compareTo(Duration.ofMinutes(1)) >= 0;
    }

    @AssertTrue(message = "blog.view.daily-retention은 1일 이상이어야 합니다")
    public boolean isDailyRetentionAtLeastOneDay() {
        return dailyRetention == null || dailyRetention.compareTo(Duration.ofDays(1)) >= 0;
    }

    /** 보관 일수 (일 단위로 내림). */
    public int dailyRetentionDays() {
        return (int) dailyRetention.toDays();
    }

    /**
     * @param limit 창 안에서 허용하는 횟수
     * @param window 창 길이
     */
    public record RateLimit(
            @Min(1) @DefaultValue("60") int limit, @NotNull @DefaultValue("1m") Duration window) {}
}
