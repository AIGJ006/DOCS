package com.team.blog.interaction.application;

import com.team.blog.account.application.BlogOwner;
import com.team.blog.account.application.MemberQueryService;
import com.team.blog.interaction.domain.FollowReasonCode;
import com.team.blog.interaction.infra.FollowRepository;
import com.team.blog.shared.error.ApiException;
import com.team.blog.shared.error.BusinessRuleException;
import com.team.blog.shared.error.CommonReasonCode;
import com.team.blog.shared.error.NotFoundException;
import com.team.blog.shared.event.MemberFollowed;
import com.team.blog.shared.event.MemberUnfollowed;
import com.team.blog.shared.infra.ratelimit.RateLimiter;
import com.team.blog.shared.security.AccountStatusGuard;
import com.team.blog.shared.security.ActionKind;
import com.team.blog.shared.security.Viewer;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 팔로우·언팔로우 (010 T017, {@code PUT·DELETE /api/members/{handle}/follow}, research R3·R4, FR-001~008).
 *
 * <p>판정 순서(README 2026-10-07, 42 §3, 처음 걸린 단계의 응답):
 *
 * <ol>
 *   <li>① 비회원 → 401 {@code LOGIN_REQUIRED}
 *   <li>② 탈퇴 유예 → 403 {@code ACCOUNT_WITHDRAWN}(001 게이트 필터), 남은 세션의 정지 → 403 {@code
 *       ACCOUNT_SUSPENDED}(001 {@link AccountStatusGuard}, {@link ActionKind#ACCOUNT_WRITE} — 이메일
 *       인증 전은 통과, F-5)
 *   <li>③ 대상 — 001 {@link MemberQueryService#findReadableBlogOwner}: 없는 주소·탈퇴 유예·익명 처리·대문자 섞인 주소는
 *       같은 404
 *   <li>④ 대상 = 나 → 400 {@code CANNOT_FOLLOW_SELF}
 *   <li>⑤ 요청 제한 {@code ratelimit:follow:{memberId}} 팔로우·언팔로우 합쳐 1분 30번 → 429 {@code
 *       TOO_MANY_REQUESTS} + {@code Retry-After}. 트랜잭션 밖이고, 앞 단계에서 걸린 요청은 세지 않는다. Redis 장애면 통과
 * </ol>
 *
 * 그다음 한 트랜잭션에서 {@code INSERT … ON CONFLICT DO NOTHING RETURNING}(언팔로우는 {@code DELETE … RETURNING})이
 * 행을 돌려줄 때만 {@link MemberFollowed}/{@link MemberUnfollowed}를 발행하고(커밋 후 전달), 같은 트랜잭션에서 최신 팔로워 수를 센다.
 * 관리자도 일반 회원과 같다(FR-008). 정지된 <b>대상</b>은 블로그가 보이므로 팔로우할 수 있다(42 P-7). 트랜잭션 안에서는 Redis를 쓰지 않는다.
 */
@Service
public class FollowService {

    private static final Logger log = LoggerFactory.getLogger(FollowService.class);

    /** 요청 제한 키 접두어 (research R13, 002 이후 {@code ratelimit:} 규칙). */
    public static final String RATE_LIMIT_PREFIX = "ratelimit:follow:";

    /**
     * 지금 상태 (응답 {@code {following, followerCount}}).
     *
     * @param following 지금 내가 팔로우 중인가
     * @param followerCount 대상의 최신 팔로워 수 (탈퇴 유예 회원 제외, 다른 사람 변화 포함)
     */
    public record FollowState(boolean following, long followerCount) {}

    private final AccountStatusGuard accountStatusGuard;
    private final MemberQueryService members;
    private final RateLimiter rateLimiter;
    private final FollowRepository follows;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;
    private final FollowProperties properties;
    private final Clock clock;

    public FollowService(
            AccountStatusGuard accountStatusGuard,
            MemberQueryService members,
            RateLimiter rateLimiter,
            FollowRepository follows,
            ApplicationEventPublisher events,
            TransactionTemplate tx,
            FollowProperties properties,
            Clock clock) {
        this.accountStatusGuard = accountStatusGuard;
        this.members = members;
        this.rateLimiter = rateLimiter;
        this.follows = follows;
        this.events = events;
        this.tx = tx;
        this.properties = properties;
        this.clock = clock;
    }

    /** 팔로우 상태로. 이미 팔로우 중이면 변화·이벤트 없이 지금 상태. */
    public FollowState follow(Viewer viewer, String handle) {
        return change(viewer, handle, true);
    }

    /** 해제 상태로. 이미 해제 상태면 변화·이벤트 없이 지금 상태. */
    public FollowState unfollow(Viewer viewer, String handle) {
        return change(viewer, handle, false);
    }

    private FollowState change(Viewer viewer, String handle, boolean follow) {
        if (viewer == null || !viewer.isAuthenticated()) {
            throw new ApiException(CommonReasonCode.LOGIN_REQUIRED);
        }
        long me = viewer.id();
        accountStatusGuard.requireActive(me, ActionKind.ACCOUNT_WRITE);
        BlogOwner target =
                members.findReadableBlogOwner(handle)
                        .orElseThrow(() -> new NotFoundException("follow target not found"));
        if (target.id() == me) {
            throw new BusinessRuleException(FollowReasonCode.CANNOT_FOLLOW_SELF);
        }
        FollowProperties.RateLimit limit = properties.rateLimit();
        rateLimiter.acquireOrThrow(RATE_LIMIT_PREFIX + me, limit.limit(), limit.window());

        return tx.execute(status -> apply(me, target.id(), follow));
    }

    private FollowState apply(long me, long target, boolean follow) {
        Instant now = clock.instant();
        boolean changed =
                follow
                        ? follows.insertIfAbsent(me, target, now)
                        : follows.deleteIfPresent(me, target);
        if (changed) {
            events.publishEvent(
                    follow
                            ? new MemberFollowed(me, target, now)
                            : new MemberUnfollowed(me, target, now));
        }
        long count = follows.countFollowers(target);
        log.debug("팔로우 {} target={} changed={}", follow ? "지정" : "해제", target, changed);
        return new FollowState(follow, count);
    }
}
