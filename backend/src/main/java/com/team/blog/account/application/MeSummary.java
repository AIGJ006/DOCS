package com.team.blog.account.application;

/**
 * 현재 로그인 상태 요약 (contracts {@code MeSummary}, {@code GET /api/me}). 화면 머리말·라우팅용.
 *
 * @param profileImageUrl 현재 프로필 사진(작은 사진 = {@code ProfileImageKeys.display()}) 주소. 없으면 null → 화면이
 *     기본 아바타를 그린다
 */
public record MeSummary(
        String handle,
        String nickname,
        String role,
        String status,
        String provider,
        boolean emailVerified,
        boolean reagreementRequired,
        String profileImageUrl) {}
