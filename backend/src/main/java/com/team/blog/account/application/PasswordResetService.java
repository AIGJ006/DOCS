package com.team.blog.account.application;

import com.team.blog.account.application.mail.PasswordResetMailRequested;
import com.team.blog.account.application.policy.PasswordPolicy;
import com.team.blog.account.domain.AuthIdentity;
import com.team.blog.account.infra.AccountProperties;
import com.team.blog.account.infra.AuthIdentityRepository;
import com.team.blog.account.infra.redis.AuthTokenStore;
import com.team.blog.account.infra.redis.TokenType;
import com.team.blog.shared.error.BusinessRuleException;
import com.team.blog.shared.error.FieldError;
import com.team.blog.shared.error.TemporarilyUnavailableException;
import com.team.blog.shared.error.ValidationException;
import com.team.blog.shared.infra.ratelimit.RateLimiter;
import com.team.blog.shared.infra.redis.RedisGuard;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 비밀번호 찾기·재설정 (FR-042~044, R-11·R-12·R-29, SC-004).
 *
 * <ul>
 *   <li>{@link #request}: 이메일 형식만 검사 → 요청 제한(같은 이메일 1분 1번·하루 10번, 같은 IP 1시간 20번 — 가입 여부와 무관) → 계정을
 *       조회하기 <b>전에</b> 접수(202)하고, 조회·발송은 {@code mailExecutor}가 한다. 토큰을 저장할 수 없으면(Redis 장애) 503.
 *   <li>{@link #confirm}: 토큰을 쓰지 않고 회원을 본 뒤 새 비밀번호 규칙을 먼저 검사(실패하면 토큰이 남는다) → 토큰을 한 번 쓴다 → BCrypt 저장
 *       → 그 회원의 모든 세션 삭제(모든 기기 로그아웃). 정지·탈퇴 유예 회원도 할 수 있다(42 §9).
 * </ul>
 */
@Service
public class PasswordResetService {

    static final String EMAIL_KEY = "rl:reset:email:";
    static final String EMAIL_DAY_KEY = "rl:reset:email-day:";
    static final String IP_KEY = "rl:reset:ip:";
    private static final Duration DAY_WINDOW = Duration.ofDays(2);
    private static final Duration HOUR = Duration.ofHours(1);
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    private final AuthIdentityRepository authIdentities;
    private final AuthTokenStore tokenStore;
    private final RateLimiter rateLimiter;
    private final RedisGuard redisGuard;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwordEncoder;
    private final SessionTerminator sessionTerminator;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transaction;
    private final AccountProperties.Reset settings;
    private final Clock clock;
    private final ZoneId zone;

    public PasswordResetService(
            AuthIdentityRepository authIdentities,
            AuthTokenStore tokenStore,
            RateLimiter rateLimiter,
            RedisGuard redisGuard,
            PasswordPolicy passwordPolicy,
            PasswordEncoder passwordEncoder,
            SessionTerminator sessionTerminator,
            ApplicationEventPublisher events,
            PlatformTransactionManager transactionManager,
            AccountProperties properties,
            Clock clock,
            ZoneId serviceZoneId) {
        this.authIdentities = authIdentities;
        this.tokenStore = tokenStore;
        this.rateLimiter = rateLimiter;
        this.redisGuard = redisGuard;
        this.passwordPolicy = passwordPolicy;
        this.passwordEncoder = passwordEncoder;
        this.sessionTerminator = sessionTerminator;
        this.events = events;
        this.transaction = new TransactionTemplate(transactionManager);
        this.settings = properties.auth().reset();
        this.clock = clock;
        this.zone = serviceZoneId;
    }

    /** 비밀번호 찾기 접수. 결과는 가입 여부와 무관하게 같다. */
    public void request(String rawEmail, String clientIp) {
        String email = EmailAddress.normalize(rawEmail);
        if (!EmailAddress.isValid(email)) {
            throw new ValidationException(
                    List.of(
                            new FieldError(
                                    "email",
                                    AccountReasonCode.EMAIL_INVALID_FORMAT.code(),
                                    AccountReasonCode.EMAIL_INVALID_FORMAT.defaultMessage())));
        }
        if (!redisGuard.isAvailable()) {
            throw new TemporarilyUnavailableException();
        }
        String hash = sha256(email);
        String today = LocalDate.now(clock.withZone(zone)).format(DAY);
        rateLimiter.acquireOrThrow(IP_KEY + clientIp, settings.ipLimitPerHour(), HOUR);
        rateLimiter.acquireOrThrow(EMAIL_KEY + hash, 1, settings.emailInterval());
        rateLimiter.acquireOrThrow(
                EMAIL_DAY_KEY + hash + ":" + today, settings.emailDailyLimit(), DAY_WINDOW);
        events.publishEvent(new PasswordResetMailRequested(email));
    }

    /** 재설정 링크로 새 비밀번호 저장. */
    public void confirm(String token, String newPassword, String newPasswordConfirm) {
        long memberId =
                tokenStore.peek(TokenType.RESET, token).orElseThrow(PasswordResetService::expired);
        AuthIdentity identity =
                authIdentities
                        .findByMemberId(memberId)
                        .filter(AuthIdentity::isLocal)
                        .orElseThrow(PasswordResetService::expired);
        List<FieldError> violations =
                passwordPolicy.violations(
                        newPassword,
                        newPasswordConfirm,
                        identity.getEmail(),
                        "newPassword",
                        "newPasswordConfirm");
        if (!violations.isEmpty()) {
            throw new ValidationException(violations);
        }
        Optional<Long> consumed = tokenStore.consume(TokenType.RESET, token);
        if (consumed.isEmpty() || consumed.get() != memberId) {
            throw expired();
        }
        String hash = passwordEncoder.encode(newPassword);
        transaction.executeWithoutResult(
                status -> {
                    authIdentities
                            .findByMemberId(memberId)
                            .orElseThrow(PasswordResetService::expired)
                            .changePassword(hash);
                    // 세션을 지우지 못하면(Redis 장애) 비밀번호 저장도 되돌린다.
                    sessionTerminator.terminateAll(memberId, Optional.empty());
                });
    }

    private static BusinessRuleException expired() {
        return new BusinessRuleException(AccountReasonCode.LINK_EXPIRED);
    }

    static String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
