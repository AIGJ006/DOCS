package com.team.blog.account.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * 회원 탈퇴·복구 설정값 ({@code blog.withdraw}, 015 data-model §9, research R16, constitution VII). 기본값은
 * {@code application.yml}에도 같게 둔다. 비밀번호 잠금 수치는 001 {@code blog.auth.password-change.*}를 그대로 쓴다.
 *
 * @param gracePeriod 복구 기한 = 정리 대상 경계 (기본 30일, FR-021a). 0보다 커야 한다
 * @param suspendedPurgeAfter 영구 정지가 시작된 뒤 자동 정리까지 (기본 365일, FR-008). 0보다 커야 한다
 * @param confirmText 소셜 가입 회원의 본인 확인 문구 (기본 "탈퇴")
 * @param purge 30일 정리 작업
 */
@Validated
@ConfigurationProperties("blog.withdraw")
public record WithdrawalProperties(
        @NotNull @DefaultValue("30d") Duration gracePeriod,
        @NotNull @DefaultValue("365d") Duration suspendedPurgeAfter,
        @NotBlank @DefaultValue("탈퇴") String confirmText,
        @Valid @NotNull @DefaultValue Purge purge) {

    public WithdrawalProperties {
        requirePositive("blog.withdraw.grace-period", gracePeriod);
        requirePositive("blog.withdraw.suspended-purge-after", suspendedPurgeAfter);
    }

    /**
     * 정리 작업 (research R8~R10).
     *
     * @param cron 실행 시각 ({@code blog.time-zone}, 기본 03:00 — 03:30 사진 정리보다 먼저)
     * @param batchSize 한 번에 처리할 최대 회원 수 (탈퇴·영구 정지 각각, 1 이상)
     * @param requiredOrders 이 단계 중 하나라도 Bean이 없으면 작업이 아무 회원도 처리하지 않는다 (research R9)
     * @param redisKeyTemplates 정리 커밋 뒤 지울 Redis 키. {@code {memberId}}·{@code {emailHash}}를 채운다
     *     (R10). 회원 번호로 Redis 키를 새로 만드는 기능은 여기에 한 줄을 더한다
     */
    public record Purge(
            @NotBlank @DefaultValue("0 0 3 * * *") String cron,
            @Min(1) @DefaultValue("100") int batchSize,
            @NotNull @DefaultValue({"10", "20", "30", "40", "50", "60", "65", "70", "80", "90"})
                    List<Integer> requiredOrders,
            @NotNull
                    @DefaultValue({
                        "auth:pw-change-fail:{memberId}",
                        "auth:login-fail:{emailHash}",
                        "rl:verify-resend:{memberId}",
                        "ratelimit:autosave:{memberId}",
                        "ratelimit:preview:{memberId}",
                        "ratelimit:like:{memberId}",
                        "ratelimit:comment:{memberId}",
                        "ratelimit:comment-edit:{memberId}",
                        "ratelimit:image:{memberId}",
                        "ratelimit:tag-suggest:{memberId}",
                        "ratelimit:follow:{memberId}"
                    })
                    List<String> redisKeyTemplates) {

        public Purge {
            requiredOrders = requiredOrders == null ? List.of() : List.copyOf(requiredOrders);
            redisKeyTemplates =
                    redisKeyTemplates == null ? List.of() : List.copyOf(redisKeyTemplates);
        }
    }

    private static void requirePositive(String name, Duration value) {
        if (value != null && (value.isNegative() || value.isZero())) {
            throw new IllegalArgumentException(name + "는 0보다 커야 합니다");
        }
    }
}
