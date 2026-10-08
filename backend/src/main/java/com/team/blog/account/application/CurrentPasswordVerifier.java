package com.team.blog.account.application;

import com.team.blog.account.domain.AuthIdentity;
import com.team.blog.account.infra.redis.PasswordChangeFailureCounter;
import com.team.blog.shared.error.BusinessRuleException;
import com.team.blog.shared.error.FieldError;
import com.team.blog.shared.error.TooManyRequestsException;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 현재 비밀번호 확인 — 비밀번호 변경(001 FR-045)과 탈퇴(015 FR-006)가 함께 쓴다 (015 T014, research R3, Clarifications
 * Q3).
 *
 * <p>순서: 잠금 확인({@code auth:pw-change-fail:{memberId}}, {@code blog.auth.password-change.*} 5번·15분 →
 * 429 {@code PASSWORD_CHANGE_TEMPORARILY_LOCKED} + {@code Retry-After}) → 비었으면 400 {@code
 * CURRENT_PASSWORD_MISMATCH}(세지 않음) → BCrypt 비교(틀리면 실패 기록 +1, 400) → 맞으면 실패 기록 삭제. 실패 기록이 하나라 두 화면
 * 실패가 합쳐 세어진다. Redis 장애면 잠금·기록 없이 비교만 한다(001 규칙 — {@code RedisFailureCounter}).
 *
 * <p>트랜잭션 밖에서 부른다(Redis 쓰기). 비밀번호 원문은 로그·예외에 넣지 않는다.
 */
@Component
public class CurrentPasswordVerifier {

    private final PasswordChangeFailureCounter failures;
    private final PasswordEncoder passwordEncoder;

    public CurrentPasswordVerifier(
            PasswordChangeFailureCounter failures, PasswordEncoder passwordEncoder) {
        this.failures = failures;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * @param identity 이메일 가입(LOCAL) 로그인 수단
     * @param rawPassword 입력한 현재 비밀번호
     * @param field 칸 오류 이름 (비밀번호 변경 {@code currentPassword}, 탈퇴 {@code password})
     */
    public void verify(AuthIdentity identity, String rawPassword, String field) {
        long memberId = identity.getMemberId();
        OptionalLong locked = failures.lockedFor(memberId);
        if (locked.isPresent()) {
            throw new TooManyRequestsException(
                    AccountReasonCode.PASSWORD_CHANGE_TEMPORARILY_LOCKED, locked.getAsLong());
        }
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw mismatch(field, OptionalLong.empty());
        }
        if (!passwordEncoder.matches(rawPassword, identity.getPasswordHash())) {
            throw mismatch(field, failures.recordFailure(memberId));
        }
        failures.reset(memberId);
    }

    private static BusinessRuleException mismatch(String field, OptionalLong nowLocked) {
        AccountReasonCode code = AccountReasonCode.CURRENT_PASSWORD_MISMATCH;
        return new BusinessRuleException(
                code,
                code.defaultMessage(),
                List.of(new FieldError(field, code.code(), code.defaultMessage())),
                nowLocked.isPresent() ? Map.of("lockedForSeconds", nowLocked.getAsLong()) : null);
    }
}
