package com.team.blog.account.application;

import com.team.blog.account.domain.MemberStatus;
import java.time.Instant;

/**
 * 로그인 성공 처리 결과.
 *
 * @param previousLoginAt 갱신하기 전 {@code last_login_at} (첫 로그인이면 null) — 세션 {@code previousLoginAt}
 * @param status 회원 상태 (탈퇴 유예면 화면이 015 복구 화면으로)
 * @param reagreementRequired 동의한 약관·처리방침 버전이 현재와 다름 (FR-012)
 */
public record LoginOutcome(
        Instant previousLoginAt, MemberStatus status, boolean reagreementRequired) {}
