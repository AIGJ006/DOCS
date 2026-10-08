package com.team.blog.account.application;

import com.team.blog.account.domain.AuthIdentity;
import com.team.blog.account.domain.Member;
import com.team.blog.account.domain.MemberStatus;
import com.team.blog.account.domain.Role;
import com.team.blog.account.infra.AuthIdentityRepository;
import com.team.blog.account.infra.MemberRepository;
import com.team.blog.account.infra.WithdrawalMemberRepository;
import com.team.blog.shared.error.AccountStateException;
import com.team.blog.shared.error.ApiException;
import com.team.blog.shared.error.BusinessRuleException;
import com.team.blog.shared.error.CommonReasonCode;
import com.team.blog.shared.error.FieldError;
import com.team.blog.shared.event.MemberWithdrawn;
import com.team.blog.shared.security.AccountStatusGuard;
import com.team.blog.shared.security.ActionKind;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 탈퇴 신청 (015 T024, research R3·R4, FR-002·005·006·008·009).
 *
 * <p>트랜잭션 밖 판정: 계정 상태({@code ACCOUNT_WRITE} — 유예·정지 403, 인증 전 통과) → 관리자 409 {@code
 * ADMIN_CANNOT_WITHDRAW} → 확인 체크 400 {@code WITHDRAW_CONFIRM_REQUIRED} → 본인 확인(이메일 가입: {@link
 * CurrentPasswordVerifier} — 잠금 429·틀림 400, 소셜: 확인 문구 400 {@code CONFIRM_TEXT_MISMATCH}, 횟수 안 셈).
 *
 * <p>트랜잭션: 회원 행 잠금 → 아직 ACTIVE인지 다시 확인(동시에 두 번 누르면 두 번째는 403 {@code ACCOUNT_WITHDRAWN}) → {@code
 * status = WITHDRAWN, withdrawn_at = now} → 모든 세션 삭제({@link SessionTerminator} — Redis 장애면 503이고 전체
 * 롤백) → {@link MemberWithdrawn}. 지금 요청의 세션 무효화는 웹 계층이 한다. 로그에는 회원 번호만 남긴다.
 */
@Service
public class WithdrawalService {

    private static final Logger log = LoggerFactory.getLogger(WithdrawalService.class);

    private final AccountStatusGuard statusGuard;
    private final MemberRepository members;
    private final AuthIdentityRepository authIdentities;
    private final CurrentPasswordVerifier passwordVerifier;
    private final WithdrawalMemberRepository withdrawals;
    private final SessionTerminator sessionTerminator;
    private final WithdrawalPolicy policy;
    private final WithdrawalProperties properties;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transaction;

    public WithdrawalService(
            AccountStatusGuard statusGuard,
            MemberRepository members,
            AuthIdentityRepository authIdentities,
            CurrentPasswordVerifier passwordVerifier,
            WithdrawalMemberRepository withdrawals,
            SessionTerminator sessionTerminator,
            WithdrawalPolicy policy,
            WithdrawalProperties properties,
            ApplicationEventPublisher events,
            PlatformTransactionManager transactionManager) {
        this.statusGuard = statusGuard;
        this.members = members;
        this.authIdentities = authIdentities;
        this.passwordVerifier = passwordVerifier;
        this.withdrawals = withdrawals;
        this.sessionTerminator = sessionTerminator;
        this.policy = policy;
        this.properties = properties;
        this.events = events;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * @return 복구 기한 ({@code withdrawn_at + grace})
     */
    public Instant withdraw(long memberId, WithdrawCommand command) {
        statusGuard.requireActive(memberId, ActionKind.ACCOUNT_WRITE);
        Member member =
                members.findById(memberId)
                        .filter(m -> !m.isDeleted())
                        .orElseThrow(() -> new ApiException(CommonReasonCode.LOGIN_REQUIRED));
        if (member.getRole() == Role.ADMIN) {
            throw new BusinessRuleException(AccountReasonCode.ADMIN_CANNOT_WITHDRAW);
        }
        if (!command.isConfirmed()) {
            throw new BusinessRuleException(AccountReasonCode.WITHDRAW_CONFIRM_REQUIRED);
        }
        AuthIdentity identity =
                authIdentities
                        .findByMemberId(memberId)
                        .orElseThrow(() -> new ApiException(CommonReasonCode.LOGIN_REQUIRED));
        if (identity.isLocal()) {
            passwordVerifier.verify(identity, command.password(), "password");
        } else if (!WithdrawConfirmText.matches(command.confirmText(), properties.confirmText())) {
            AccountReasonCode code = AccountReasonCode.CONFIRM_TEXT_MISMATCH;
            throw new BusinessRuleException(
                    code,
                    code.defaultMessage(),
                    List.of(new FieldError("confirmText", code.code(), code.defaultMessage())),
                    null);
        }
        Instant now = policy.now();
        transaction.executeWithoutResult(
                status -> {
                    WithdrawalMemberRepository.LockedMember locked =
                            withdrawals
                                    .lockForUpdate(memberId)
                                    .filter(m -> !m.isDeleted())
                                    .orElseThrow(
                                            () ->
                                                    new ApiException(
                                                            CommonReasonCode.LOGIN_REQUIRED));
                    requireStillActive(locked.status());
                    if (withdrawals.markWithdrawn(memberId, now) != 1) {
                        throw new AccountStateException(
                                CommonReasonCode.ACCOUNT_WITHDRAWN,
                                AccountStatusGuardService.RESTORE_DETAILS);
                    }
                    sessionTerminator.terminateAll(memberId, Optional.empty());
                    events.publishEvent(new MemberWithdrawn(memberId, now));
                });
        log.info("탈퇴 신청 memberId={}", memberId);
        return policy.deadline(now);
    }

    private static void requireStillActive(MemberStatus status) {
        switch (status) {
            case WITHDRAWN ->
                    throw new AccountStateException(
                            CommonReasonCode.ACCOUNT_WITHDRAWN,
                            AccountStatusGuardService.RESTORE_DETAILS);
            case SUSPENDED -> throw new AccountStateException(CommonReasonCode.ACCOUNT_SUSPENDED);
            case ACTIVE -> {}
        }
    }
}
