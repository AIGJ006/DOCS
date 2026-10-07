package com.team.blog.account.application;

import com.team.blog.account.application.mail.VerificationMailRequested;
import com.team.blog.account.domain.AuthIdentity;
import com.team.blog.account.infra.AccountProperties;
import com.team.blog.account.infra.AuthIdentityRepository;
import com.team.blog.account.infra.redis.AuthTokenStore;
import com.team.blog.account.infra.redis.TokenType;
import com.team.blog.shared.error.ApiException;
import com.team.blog.shared.error.BusinessRuleException;
import com.team.blog.shared.error.CommonReasonCode;
import com.team.blog.shared.error.TemporarilyUnavailableException;
import com.team.blog.shared.infra.ratelimit.RateLimiter;
import com.team.blog.shared.infra.redis.RedisGuard;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이메일 인증 (FR-005·006, 07 §3, L-2·L-10, R-11).
 *
 * <ul>
 *   <li>재발송: 이미 인증 409 {@code ALREADY_VERIFIED}, 회원당 1분 1번({@code rl:verify-resend:{memberId}}), 하루
 *       10번({@code auth:verify-resend:{memberId}:{yyyyMMdd}}, 날짜는 {@code blog.time-zone}, TTL 2일) →
 *       초과 429. 통과하면 커밋 후 새 링크를 보낸다(이전 링크 무효). 새 토큰을 저장할 수 없으면(Redis 장애) 503.
 *   <li>확인: 토큰을 원자적으로 한 번 쓴다. 없거나·만료·이전 링크면 400 {@code LINK_EXPIRED}. 성공하면 {@code email_verified_at
 *       = now}.
 * </ul>
 */
@Service
public class EmailVerificationService {

    static final Duration DAILY_WINDOW = Duration.ofDays(2);
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    private final AuthIdentityRepository authIdentities;
    private final AuthTokenStore tokenStore;
    private final RateLimiter rateLimiter;
    private final RedisGuard redisGuard;
    private final ApplicationEventPublisher events;
    private final AccountProperties.Verify settings;
    private final Clock clock;
    private final ZoneId zone;

    public EmailVerificationService(
            AuthIdentityRepository authIdentities,
            AuthTokenStore tokenStore,
            RateLimiter rateLimiter,
            RedisGuard redisGuard,
            ApplicationEventPublisher events,
            AccountProperties properties,
            Clock clock,
            ZoneId serviceZoneId) {
        this.authIdentities = authIdentities;
        this.tokenStore = tokenStore;
        this.rateLimiter = rateLimiter;
        this.redisGuard = redisGuard;
        this.events = events;
        this.settings = properties.auth().verify();
        this.clock = clock;
        this.zone = serviceZoneId;
    }

    /** 인증 메일 다시 보내기 (202). */
    @Transactional(readOnly = true)
    public void resend(long memberId) {
        AuthIdentity identity =
                authIdentities
                        .findByMemberId(memberId)
                        .orElseThrow(() -> new ApiException(CommonReasonCode.LOGIN_REQUIRED));
        if (identity.isEmailVerified()) {
            throw new BusinessRuleException(AccountReasonCode.ALREADY_VERIFIED);
        }
        if (!redisGuard.isAvailable()) {
            throw new TemporarilyUnavailableException();
        }
        rateLimiter.acquireOrThrow("rl:verify-resend:" + memberId, 1, settings.resendInterval());
        String today = LocalDate.now(clock.withZone(zone)).format(DAY);
        rateLimiter.acquireOrThrow(
                "auth:verify-resend:" + memberId + ":" + today,
                settings.resendDailyLimit(),
                DAILY_WINDOW);
        events.publishEvent(new VerificationMailRequested(memberId));
    }

    /** 인증 링크 확인. */
    @Transactional
    public void confirm(String token) {
        long memberId =
                tokenStore
                        .consume(TokenType.VERIFY, token)
                        .orElseThrow(
                                () -> new BusinessRuleException(AccountReasonCode.LINK_EXPIRED));
        authIdentities
                .findByMemberId(memberId)
                .orElseThrow(() -> new BusinessRuleException(AccountReasonCode.LINK_EXPIRED))
                .verifyEmail(clock.instant());
    }
}
