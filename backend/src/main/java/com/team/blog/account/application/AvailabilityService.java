package com.team.blog.account.application;

import com.team.blog.account.application.policy.HandlePolicy;
import com.team.blog.account.application.policy.HandleSuggester;
import com.team.blog.account.application.policy.NicknameCheck;
import com.team.blog.account.application.policy.NicknamePolicy;
import com.team.blog.account.domain.Provider;
import com.team.blog.account.infra.AccountProperties;
import com.team.blog.account.infra.MemberRepository;
import com.team.blog.shared.infra.ratelimit.RateLimiter;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 블로그 주소·닉네임 사용 가능 확인 (FR-020·026, 08 §4-2, 09 §6). 안내용이며 최종 판정은 가입·수정 요청에서 다시 한다. 같은 IP 1분 {@code
 * blog.availability.ip-limit-per-minute}(30)번, 넘으면 429 {@code TOO_MANY_REQUESTS}(Redis 장애 시 통과).
 */
@Service
public class AvailabilityService {

    static final String HANDLE_KEY = "rl:availability:handle:ip:";
    static final String NICKNAME_KEY = "rl:availability:nickname:ip:";
    private static final Duration WINDOW = Duration.ofMinutes(1);

    /** 주소 확인 결과 (contracts {@code HandleAvailability}). */
    public record HandleAvailability(
            boolean available, AccountReasonCode reason, String suggestion) {}

    /** 닉네임 확인 결과 (contracts {@code NicknameAvailability}). */
    public record NicknameAvailability(boolean available, AccountReasonCode code) {}

    private final HandlePolicy handlePolicy;
    private final HandleSuggester handleSuggester;
    private final NicknamePolicy nicknamePolicy;
    private final MemberRepository members;
    private final RateLimiter rateLimiter;
    private final int ipLimit;

    public AvailabilityService(
            HandlePolicy handlePolicy,
            HandleSuggester handleSuggester,
            NicknamePolicy nicknamePolicy,
            MemberRepository members,
            RateLimiter rateLimiter,
            AccountProperties properties) {
        this.handlePolicy = handlePolicy;
        this.handleSuggester = handleSuggester;
        this.nicknamePolicy = nicknamePolicy;
        this.members = members;
        this.rateLimiter = rateLimiter;
        this.ipLimit = properties.availability().ipLimitPerMinute();
    }

    /**
     * 접두어({@code go-}·{@code gi-}) 포함 전체 주소. 형식 → 예약어 → 금칙어 → 중복 순. 예약어·중복이면 {@code _2}, {@code _3}
     * … 중 비어 있는 첫 대안.
     */
    public HandleAvailability checkHandle(String raw, String clientIp) {
        rateLimiter.acquireOrThrow(HANDLE_KEY + clientIp, ipLimit, WINDOW);
        String handle = raw == null ? "" : raw.strip();
        List<AccountReasonCode> failures = handlePolicy.validate(handle, providerOf(handle));
        if (!failures.isEmpty()) {
            AccountReasonCode reason = failures.getFirst();
            String suggestion =
                    reason == AccountReasonCode.HANDLE_RESERVED
                            ? handleSuggester.nextAvailable(handle)
                            : null;
            return new HandleAvailability(false, reason, suggestion);
        }
        if (members.existsByHandle(handle)) {
            return new HandleAvailability(
                    false,
                    AccountReasonCode.HANDLE_DUPLICATE,
                    handleSuggester.nextAvailable(handle));
        }
        return new HandleAvailability(true, null, null);
    }

    /** 09 §3 ①~⑥. 로그인한 회원이면 자기 자신은 중복에서 뺀다(대소문자만 바꾸기). */
    public NicknameAvailability checkNickname(String raw, Long selfMemberId, String clientIp) {
        rateLimiter.acquireOrThrow(NICKNAME_KEY + clientIp, ipLimit, WINDOW);
        NicknameCheck check = nicknamePolicy.check(raw, selfMemberId);
        return check.valid()
                ? new NicknameAvailability(true, null)
                : new NicknameAvailability(false, check.failure());
    }

    private static Provider providerOf(String handle) {
        return switch (HandlePolicy.prefixOf(handle)) {
            case "go-" -> Provider.GOOGLE;
            case "gi-" -> Provider.GITHUB;
            default -> Provider.LOCAL;
        };
    }
}
