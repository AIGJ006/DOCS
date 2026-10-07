package com.team.blog.post.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * 글 작성·자동 저장·발행·정리 설정값 ({@code blog.autosave}·{@code blog.post}·{@code blog.publish}·{@code
 * blog.cleanup}, 002 plan Constraints, constitution VII). 기본값은 {@code application.yml}에 둔다.
 *
 * <p>{@code blog.image.*}는 001 {@code CoreProperties.Image}가 바인딩하고 여기서 다시 바인딩하지 않는다. 렌더러 설정은 {@code
 * shared.application.markdown.MarkdownProperties}({@code blog.markdown}).
 */
@Validated
@ConfigurationProperties("blog")
public record PostAuthoringProperties(
        @Valid @NotNull @DefaultValue Autosave autosave,
        @Valid @NotNull @DefaultValue Post post,
        @Valid @NotNull @DefaultValue Publish publish,
        @Valid @NotNull @DefaultValue Cleanup cleanup) {

    /**
     * @param redisTtl {@code autosave:post:{id}} 보관 기간 (저장마다 연장, 기본 24h)
     * @param flushInterval Redis → DB 반영 주기 (기본 1m)
     * @param rateLimit 사용자당 자동 저장 요청 제한 (기본 5초에 1번)
     * @param maxRequestBytes 자동 저장 요청 본문 최대 크기 (기본 1MB, 넘으면 413)
     */
    public record Autosave(
            @NotNull @DefaultValue("24h") Duration redisTtl,
            @NotNull @DefaultValue("1m") Duration flushInterval,
            @Valid @NotNull @DefaultValue RateLimit rateLimit,
            @Min(1) @DefaultValue("1048576") long maxRequestBytes) {}

    /**
     * @param maxTags 글 하나의 태그 최대 수 (기본 10, DB {@code ck_post_tag_position} 0~99 안)
     * @param titleMax 제목 최대 글자 수 (기본 100 = DB {@code varchar(100)})
     * @param contentMax 본문 최대 글자 수 (기본 100,000 = DB {@code ck_post_content})
     */
    public record Post(
            @Min(0) @Max(100) @DefaultValue("10") int maxTags,
            @Min(1) @Max(100) @DefaultValue("100") int titleMax,
            @Min(1) @Max(100_000) @DefaultValue("100000") int contentMax) {}

    /**
     * @param idempotencyTtl 발행 멱등 키 보관 기간 (기본 600s)
     */
    public record Publish(@NotNull @DefaultValue("600s") Duration idempotencyTtl) {}

    /**
     * @param emptyDraftCron 빈 임시글 정리 시각 (기본 매일 03:30, {@code zone}의 시각)
     * @param zone cron 시간대 (기본 Asia/Seoul)
     * @param emptyDraftAge 만든 뒤 이만큼 지난 빈 임시글만 지운다 (기본 24h)
     */
    public record Cleanup(
            @NotBlank @DefaultValue("0 30 3 * * *") String emptyDraftCron,
            @NotBlank @DefaultValue("Asia/Seoul") String zone,
            @NotNull @DefaultValue("24h") Duration emptyDraftAge) {}

    /**
     * 자동 저장 요청 제한 ({@code RateLimiter.tryAcquire("ratelimit:autosave:{memberId}", limit, window)}).
     *
     * @param limit 창 안에서 허용하는 횟수 (기본 1)
     * @param window 창 길이 (기본 5s)
     */
    public record RateLimit(
            @Min(1) @DefaultValue("1") int limit, @NotNull @DefaultValue("5s") Duration window) {}
}
