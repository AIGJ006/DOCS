package com.team.blog.moderation.application;

import com.team.blog.account.application.SuspensionDuration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * 신고·숨김·정지 설정값 ({@code blog.moderation.*}, 014 research R14, data-model §7, constitution VII). 기본값은
 * {@code application.yml}에도 같게 둔다.
 *
 * @param snapshotContentChars 글 스냅샷 원문 앞부분 길이 (코드 포인트, 기본 2,000 — V1 {@code snapshot_content
 *     varchar(2000)})
 * @param detailMaxChars 기타 설명 최대 길이 (코드 포인트, 기본 200 — V1 {@code detail varchar(200)})
 * @param rateLimit 회원당 신고 요청 제한 (1분 5건·하루 50건, FR-009)
 * @param retention 처리된 사건의 스냅샷·설명 보관 기간 (기본 30일, FR-033)
 * @param cleanupCron 보관 정리 시각 (기본 매일 04:45, {@code blog.time-zone})
 * @param cleanupBatchSize 보관 정리 한 번에 비우는 행 수 (기본 1,000)
 * @param adminPageSize 관리자 사건 목록 한 페이지 (기본 20)
 * @param suspensionDurations 고를 수 있는 정지 기간 (기본 1일·7일·30일·영구)
 * @param suspensionReasonMaxChars 정지 사유 최대 길이 (기본 200 — V1 {@code member_suspension.reason})
 */
@Validated
@ConfigurationProperties("blog.moderation")
public record ModerationProperties(
        @Min(1) @Max(2000) @DefaultValue("2000") int snapshotContentChars,
        @Min(1) @Max(200) @DefaultValue("200") int detailMaxChars,
        @Valid @NotNull @DefaultValue RateLimit rateLimit,
        @NotNull @DefaultValue("30d") Duration retention,
        @NotBlank @DefaultValue("0 45 4 * * *") String cleanupCron,
        @Min(1) @DefaultValue("1000") int cleanupBatchSize,
        @Min(1) @Max(100) @DefaultValue("20") int adminPageSize,
        @NotEmpty @DefaultValue({"P1D", "P7D", "P30D", "PERMANENT"})
                List<SuspensionDuration> suspensionDurations,
        @Min(1) @Max(200) @DefaultValue("200") int suspensionReasonMaxChars) {

    /**
     * @param perMinute 1분에 허용하는 신고 요청 수
     * @param perDay 하루에 허용하는 신고 요청 수 ({@code perMinute} 이상)
     */
    public record RateLimit(
            @Min(1) @DefaultValue("5") int perMinute, @Min(1) @DefaultValue("50") int perDay) {

        /** 1분 제한이 하루 제한보다 크면 하루 제한이 먼저 걸려 1분 제한이 뜻이 없다. */
        @AssertTrue(message = "per-minute은 per-day 이하여야 합니다")
        public boolean isPerMinuteWithinPerDay() {
            return perMinute <= perDay;
        }
    }
}
