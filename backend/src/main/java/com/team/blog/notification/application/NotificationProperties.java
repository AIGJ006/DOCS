package com.team.blog.notification.application;

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
 * 알림 설정값 ({@code blog.notification}, 011 data-model §6, research R17, constitution VII). 기본값은
 * {@code application.yml}에도 같게 둔다.
 *
 * @param retention 보관 기간 (기본 90일, FR-037) — {@code updated_at}이 이보다 오래되면 정리
 * @param maxPerMember 사람당 최대 개수 (기본 1,000)
 * @param cleanup 매일 정리 작업
 * @param followDedupWindow 같은 사람의 새 팔로워 알림을 다시 만들지 않는 기간 (기본 7일, FR-014)
 * @param previewLength 댓글 미리보기 길이 (코드 포인트, 기본 50, FR-021)
 * @param previewScan 목록 SQL이 댓글 내용 앞부분만 읽는 길이 (기본 400)
 * @param dropdownSize 펼침 목록 크기 (기본 10) — 목록 API {@code size} 허용값
 * @param pageSize 전체 알림 페이지 크기 (기본 20) — 목록 API {@code size} 허용값·기본값
 */
@Validated
@ConfigurationProperties("blog.notification")
public record NotificationProperties(
        @NotNull @DefaultValue("90d") Duration retention,
        @Min(1) @DefaultValue("1000") int maxPerMember,
        @Valid @NotNull @DefaultValue Cleanup cleanup,
        @NotNull @DefaultValue("7d") Duration followDedupWindow,
        @Min(1) @Max(200) @DefaultValue("50") int previewLength,
        @Min(1) @Max(1000) @DefaultValue("400") int previewScan,
        @Min(1) @Max(100) @DefaultValue("10") int dropdownSize,
        @Min(1) @Max(100) @DefaultValue("20") int pageSize) {

    /**
     * @param cron 실행 시각 (기본 매일 04:30, 시간대는 공통 {@code blog.time-zone})
     * @param batchSize 90일 정리 한 번에 지우는 수 (기본 1,000)
     * @param recentWindow 1,000개 정리 대상 고르기 — 이 기간 안에 알림을 받은 사람만 (기본 1일)
     */
    public record Cleanup(
            @NotBlank @DefaultValue("0 30 4 * * *") String cron,
            @Min(1) @DefaultValue("1000") int batchSize,
            @NotNull @DefaultValue("1d") Duration recentWindow) {}
}
