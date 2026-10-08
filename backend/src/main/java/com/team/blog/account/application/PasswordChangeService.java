package com.team.blog.account.application;

import com.team.blog.account.application.mail.PasswordChangedMailRequested;
import com.team.blog.account.application.policy.PasswordPolicy;
import com.team.blog.account.domain.AuthIdentity;
import com.team.blog.account.infra.AuthIdentityRepository;
import com.team.blog.shared.error.ApiException;
import com.team.blog.shared.error.BusinessRuleException;
import com.team.blog.shared.error.CommonReasonCode;
import com.team.blog.shared.error.FieldError;
import com.team.blog.shared.error.ValidationException;
import com.team.blog.shared.security.AccountStatusGuard;
import com.team.blog.shared.security.ActionKind;
import java.util.List;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 로그인 상태 비밀번호 변경 (FR-045, 11 §6-2, R-12).
 *
 * <p>순서: 계정 상태({@code ACCOUNT_WRITE} — 인증 전도 가능, 정지·탈퇴 유예 403) → 이메일 가입 계정만(아니면 400 {@code
 * PASSWORD_NOT_SUPPORTED}) → 잠금(현재 비밀번호 5회 연속 실패 → 15분, 429 {@code
 * PASSWORD_CHANGE_TEMPORARILY_LOCKED}) → 현재 비밀번호 확인(틀리면 실패 횟수 +1, 400 {@code
 * CURRENT_PASSWORD_MISMATCH}) — 잠금·비교·실패 기록은 015 탈퇴와 함께 쓰는 {@link CurrentPasswordVerifier} → 비밀번호
 * 규칙 → 현재와 같으면 400 {@code PASSWORD_SAME_AS_CURRENT} → 저장 + 지금 세션을 뺀 모든 세션 삭제 → 커밋 후 알림 메일. 지금 세션의
 * ID 재발급은 웹 계층이 한다.
 */
@Service
public class PasswordChangeService {

    private final AccountStatusGuard statusGuard;
    private final AuthIdentityRepository authIdentities;
    private final CurrentPasswordVerifier currentPasswordVerifier;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwordEncoder;
    private final SessionTerminator sessionTerminator;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transaction;

    public PasswordChangeService(
            AccountStatusGuard statusGuard,
            AuthIdentityRepository authIdentities,
            CurrentPasswordVerifier currentPasswordVerifier,
            PasswordPolicy passwordPolicy,
            PasswordEncoder passwordEncoder,
            SessionTerminator sessionTerminator,
            ApplicationEventPublisher events,
            PlatformTransactionManager transactionManager) {
        this.statusGuard = statusGuard;
        this.authIdentities = authIdentities;
        this.currentPasswordVerifier = currentPasswordVerifier;
        this.passwordPolicy = passwordPolicy;
        this.passwordEncoder = passwordEncoder;
        this.sessionTerminator = sessionTerminator;
        this.events = events;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public void change(
            long memberId,
            String currentPassword,
            String newPassword,
            String newPasswordConfirm,
            String currentSessionId) {
        statusGuard.requireActive(memberId, ActionKind.ACCOUNT_WRITE);
        AuthIdentity identity =
                authIdentities
                        .findByMemberId(memberId)
                        .orElseThrow(() -> new ApiException(CommonReasonCode.LOGIN_REQUIRED));
        if (!identity.isLocal()) {
            throw new BusinessRuleException(AccountReasonCode.PASSWORD_NOT_SUPPORTED);
        }
        currentPasswordVerifier.verify(identity, currentPassword, "currentPassword");
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
        if (passwordEncoder.matches(newPassword, identity.getPasswordHash())) {
            throw new BusinessRuleException(AccountReasonCode.PASSWORD_SAME_AS_CURRENT);
        }
        String hash = passwordEncoder.encode(newPassword);
        transaction.executeWithoutResult(
                status -> {
                    authIdentities
                            .findByMemberId(memberId)
                            .orElseThrow(() -> new ApiException(CommonReasonCode.LOGIN_REQUIRED))
                            .changePassword(hash);
                    sessionTerminator.terminateAll(memberId, Optional.ofNullable(currentSessionId));
                    events.publishEvent(new PasswordChangedMailRequested(memberId));
                });
    }
}
