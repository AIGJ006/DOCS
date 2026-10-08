package com.team.blog.account.application.purge;

import com.team.blog.account.application.EmailAddress;
import com.team.blog.account.application.WithdrawalPolicy;
import com.team.blog.account.domain.MemberStatus;
import com.team.blog.account.domain.Role;
import com.team.blog.account.infra.WithdrawalMemberRepository;
import com.team.blog.account.infra.WithdrawalMemberRepository.LockedMember;
import com.team.blog.shared.application.withdraw.WithdrawalPurgeStep;
import com.team.blog.shared.infra.redis.RedisGuard;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 한 명을 한 트랜잭션으로 정리한다 (015 T055, contracts/purge-steps.md §3, research R8·R11).
 *
 * <ol>
 *   <li>회원 행을 {@code FOR UPDATE}로 잠그고 조건을 다시 확인한다. 그 사이 복구했거나 이미 익명 처리됐으면 아무것도 하지 않는다.
 *   <li>영구 정지 1년 경과면 {@code SUSPENDED → WITHDRAWN}(신청 시각 = 지금)으로 바꾼다. 정지 이력 행은 남긴다.
 *   <li>로그인 이메일의 해시를 단계 50이 로그인 수단을 지우기 전에 계산한다(커밋 뒤 Redis 키 정리용).
 *   <li>등록된 단계를 order 순으로 부른다. 하나라도 실패하면 {@link WithdrawalPurgeStepException}으로 감싸 던져 전체가 롤백된다.
 *   <li>커밋 뒤 {@link WithdrawalRedisCleaner}가 세션·토큰·횟수 키를 지운다. 롤백되면 돌지 않는다.
 * </ol>
 *
 * 이벤트도 메일도 내지 않는다(order 10이 부르는 006의 {@code PostPurged}만 예외). 로그에는 회원 번호만 남긴다.
 */
@Component
public class WithdrawalPurgeRunner {

    private static final Logger log = LoggerFactory.getLogger(WithdrawalPurgeRunner.class);

    /** 정리 이유. */
    public enum Reason {
        /** 탈퇴 신청 뒤 유예 기간 경과 */
        GRACE_EXPIRED,
        /** 영구 정지 뒤 1년 경과 (FR-008) */
        SUSPENDED_PERMANENT
    }

    /** 한 명 처리 결과. */
    public enum Outcome {
        PURGED,
        SKIPPED
    }

    private final WithdrawalMemberRepository members;
    private final WithdrawalPolicy policy;
    private final WithdrawalStepRegistry registry;
    private final WithdrawalRedisCleaner redisCleaner;

    public WithdrawalPurgeRunner(
            WithdrawalMemberRepository members,
            WithdrawalPolicy policy,
            WithdrawalStepRegistry registry,
            WithdrawalRedisCleaner redisCleaner) {
        this.members = members;
        this.policy = policy;
        this.registry = registry;
        this.redisCleaner = redisCleaner;
    }

    /**
     * @throws WithdrawalPurgeStepException 단계 하나가 실패함 (이 회원의 트랜잭션은 롤백됨)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Outcome purgeOne(long memberId, Reason reason) {
        Instant now = policy.now();
        Optional<LockedMember> locked = members.lockForUpdate(memberId);
        if (locked.isEmpty() || !stillTarget(locked.get(), reason, now)) {
            log.info("탈퇴 정리 건너뜀 memberId={} reason={}", memberId, reason);
            return Outcome.SKIPPED;
        }
        if (reason == Reason.SUSPENDED_PERMANENT) {
            members.markSuspendedAsWithdrawn(memberId, now);
        }
        String emailHash =
                members.findLoginEmail(memberId).map(WithdrawalPurgeRunner::sha256).orElse(null);
        for (WithdrawalPurgeStep step : registry.steps()) {
            try {
                step.purge(memberId);
            } catch (RuntimeException e) {
                throw new WithdrawalPurgeStepException(
                        memberId, step.order(), WithdrawalStepRegistry.name(step), e);
            }
        }
        RedisGuard.runAfterCommit(() -> redisCleaner.clean(memberId, emailHash));
        log.info("탈퇴 정리 완료 memberId={} reason={}", memberId, reason);
        return Outcome.PURGED;
    }

    private boolean stillTarget(LockedMember m, Reason reason, Instant now) {
        if (m.isDeleted()) {
            return false;
        }
        return switch (reason) {
            case GRACE_EXPIRED ->
                    m.status() == MemberStatus.WITHDRAWN
                            && m.withdrawnAt() != null
                            && policy.isPurgeTarget(m.withdrawnAt(), now);
            case SUSPENDED_PERMANENT ->
                    m.status() == MemberStatus.SUSPENDED
                            && m.role() == Role.USER
                            && members.hasOpenPermanentSuspensionBefore(
                                    m.id(), policy.suspendedPurgeCutoff(now));
        };
    }

    /**
     * {@code auth:login-fail:{emailHash}}와 같은 해시 (001 LoginFailureCounter — 정규화 이메일 SHA-256 hex).
     */
    static String sha256(String email) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(
                                            EmailAddress.normalize(email)
                                                    .getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
