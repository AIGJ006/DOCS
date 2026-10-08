package com.team.blog.account.infra;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 계정 설정값 ({@code blog.auth}·{@code blog.member}·{@code blog.agreement}·{@code blog.availability}·
 * {@code blog.policy}). 기본값은 {@code application.yml}에 data-model §6 표 그대로 둔다(constitution VII).
 *
 * <p>블로그 주소 3~36자·닉네임 2~10자·소개 최대 200자처럼 DB CHECK와 같아야 하는 값은 설정값이 아니라 코드 상수다(data-model §6 끝).
 * {@link MemberRules.Bio#maxLength()}는 200을 넘을 수 없다.
 */
@Validated
@ConfigurationProperties("blog")
public record AccountProperties(
        @Valid @NotNull Auth auth,
        @Valid @NotNull MemberRules member,
        @Valid @NotNull Agreement agreement,
        @Valid @NotNull Availability availability,
        @Valid @NotNull Policy policy) {

    /**
     * @param sessionTimeout 세션 유지 기간 (마지막 활동부터, 07 L-6)
     */
    public record Auth(
            @NotNull Duration sessionTimeout,
            @Valid @NotNull Login login,
            @Valid @NotNull Verify verify,
            @Valid @NotNull Reset reset,
            @Valid @NotNull PasswordChange passwordChange,
            @Valid @NotNull Social social) {}

    /** 07 L-7: 같은 계정 연속 실패 잠금, 같은 IP 1분 제한. */
    public record Login(
            @Min(1) int maxFailures,
            @NotNull Duration lockDuration,
            @Min(1) int ipLimitPerMinute) {}

    /** 07 §3·L-10: 인증 링크 유효 기간, 재발송 간격·하루 한도. */
    public record Verify(
            @NotNull Duration tokenTtl,
            @NotNull Duration resendInterval,
            @Min(1) int resendDailyLimit) {}

    /** 07 §4-1·L-4: 재설정 링크 유효 기간, 같은 이메일 간격·하루 한도, 같은 IP 1시간 한도. */
    public record Reset(
            @NotNull Duration tokenTtl,
            @NotNull Duration emailInterval,
            @Min(1) int emailDailyLimit,
            @Min(1) int ipLimitPerHour) {}

    /** 11 §6-2: 현재 비밀번호 연속 실패 잠금. */
    public record PasswordChange(@Min(1) int maxFailures, @NotNull Duration lockDuration) {}

    /** 07 §5, 11 §4-2: 소셜 가입 대기 보관 시간, 가입 마무리 화면에 허용하는 사진 호스트. */
    public record Social(
            @NotNull Duration pendingTtl,
            @NotEmpty List<String> photoHosts,
            ClientCredentials google,
            ClientCredentials github) {}

    /**
     * 소셜 로그인 앱 키 (환경 변수 {@code GOOGLE_CLIENT_ID}·{@code GOOGLE_CLIENT_SECRET}·{@code
     * GITHUB_CLIENT_*}, constitution IV). 비어 있으면 그 제공자는 등록하지 않고 로그인 화면 버튼도 숨긴다.
     */
    public record ClientCredentials(String clientId, String clientSecret) {

        public boolean configured() {
            return clientId != null
                    && !clientId.isBlank()
                    && clientSecret != null
                    && !clientSecret.isBlank();
        }
    }

    /** 회원 프로필 규칙. */
    public record MemberRules(
            @NotNull Duration nicknameChangeInterval,
            @Valid @NotNull Bio bio,
            @Valid @NotNull LastActive lastActive) {

        /** 11 R-1: 소개 최대 길이(코드 포인트, DB CHECK 200 이하)와 줄 수. */
        public record Bio(
                @Min(0) @jakarta.validation.constraints.Max(200) int maxLength,
                @Min(1) int maxLines) {}

        /** 06 §6-4: 최근 활동 갱신 간격. */
        public record LastActive(@NotNull Duration touchInterval) {}
    }

    /** 07 §3-1: 약관·처리방침 현재 버전과 시행일. */
    public record Agreement(@Valid @NotNull Document terms, @Valid @NotNull Document privacy) {

        public record Document(@NotBlank String version, @NotNull LocalDate effectiveDate) {}
    }

    /** 08 §4-2, 09 §6: 블로그 주소·닉네임 사용 가능 확인 같은 IP 1분 한도. */
    public record Availability(@Min(1) int ipLimitPerMinute) {}

    /** 예약어·금칙어·흔한 비밀번호 목록 파일 위치 (한 줄 한 단어, {@code #} 주석). */
    public record Policy(
            @NotBlank String reservedHandles,
            @NotBlank String reservedNicknames,
            @NotBlank String bannedWords,
            @NotBlank String bannedWordsExceptions,
            @NotBlank String commonPasswords) {}
}
