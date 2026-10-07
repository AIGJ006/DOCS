package com.team.blog.account.web.dto;

/**
 * 이메일 로그인 성공 응답 (contracts {@code LoginResult}).
 *
 * @param redirectTo 검사를 통과한 사이트 안 상대 경로 또는 {@code /} (FR-039)
 * @param reagreementRequired true면 화면은 {@code /reagree}로
 * @param accountStatus {@code ACTIVE} 또는 {@code WITHDRAWN}(탈퇴 유예 → 015 복구 화면)
 */
public record LoginResult(String redirectTo, boolean reagreementRequired, String accountStatus) {}
