package com.team.blog.shared.security;

/**
 * 계정 상태 판정 포트 (42 §3 ②, R-22). 쓰기 Service는 첫머리에서 호출한다(002·003·006·007·009·014).
 *
 * <p>판정은 세션 값이 아니라 매번 DB에서 읽은 {@code member.status}·{@code auth_identity.email_verified_at}으로 하고,
 * 우선순위는 탈퇴 유예 → 정지 → 인증 전이다. 구현은 account 모듈의 {@code AccountStatusGuardService}.
 *
 * <p>쓰기가 아닌 요청의 탈퇴 유예 차단은 {@code WithdrawnAccountGateFilter}(T042a)가 맡는다.
 */
public interface AccountStatusGuard {

    /**
     * 그 회원이 이 행동을 할 수 있는 상태인지 확인한다.
     *
     * @throws com.team.blog.shared.error.AccountStateException 403 {@code
     *     ACCOUNT_WITHDRAWN}(details {@code {action: RESTORE}})·{@code ACCOUNT_SUSPENDED}·{@code
     *     EMAIL_NOT_VERIFIED}
     * @throws com.team.blog.shared.error.ApiException 401 {@code LOGIN_REQUIRED} — 없는 회원·익명 처리된 회원
     */
    void requireActive(long memberId, ActionKind kind);
}
