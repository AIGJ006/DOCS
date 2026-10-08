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
 * 댓글 설정값 ({@code blog.comment}, 007 research R15, constitution VII). 기본값은 {@code application.yml}에도
 * 같게 둔다.
 *
 * @param pageSize 최상위 댓글 한 페이지 (기본 20, FR-016)
 * @param replyPreview 최상위마다 처음 보이는 답글 수 (기본 3, FR-017)
 * @param replyPageSize 답글 펼치기 한 번 (기본 20)
 * @param contentMax 내용 최대 코드 포인트 (기본 1000). DB {@code varchar(1000)}보다 클 수 없다
 * @param dedupeWindow 같은 요청을 새로 만들지 않는 시간 (기본 10초, FR-011)
 * @param maxDepth 답글 깊이 (공통 1 — 개인 확장이 자기 plan에서 넓힌다)
 * @param aroundMaxReplies 바로 가기 때 대상까지 펼칠 답글 상한 (기본 100, research R11)
 * @param rateLimit 작성·수정 요청 제한
 */
@Validated
@ConfigurationProperties("blog.comment")
public record CommentProperties(
        @Min(1) @Max(100) @DefaultValue("20") int pageSize,
        @Min(0) @Max(20) @DefaultValue("3") int replyPreview,
        @Min(1) @Max(100) @DefaultValue("20") int replyPageSize,
        @Min(1) @Max(1000) @DefaultValue("1000") int contentMax,
        @NotNull @DefaultValue("10s") Duration dedupeWindow,
        @Min(1) @Max(1) @DefaultValue("1") int maxDepth,
        @Min(1) @DefaultValue("100") int aroundMaxReplies,
        @Valid @NotNull @DefaultValue RateLimits rateLimit) {

    /** 작성 1분 10개, 수정 1분 20번 (FR-012·FR-029). */
    public record RateLimits(
            @Valid @NotNull @DefaultValue CreateLimit create,
            @Valid @NotNull @DefaultValue EditLimit edit) {}

    /** 작성 고정 창 한도 (001 {@code RateLimiter}). */
    public record CreateLimit(
            @Min(1) @DefaultValue("10") int limit, @NotNull @DefaultValue("1m") Duration window) {}

    /** 수정 고정 창 한도. */
    public record EditLimit(
            @Min(1) @DefaultValue("20") int limit, @NotNull @DefaultValue("1m") Duration window) {}
}
