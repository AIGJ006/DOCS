package com.team.blog.account.application;

/**
 * 현재 로그인 상태 요약 (contracts {@code MeSummary}, {@code GET /api/me}). 화면 머리말·라우팅용.
 *
 * @param memberId 본인 회원 번호. 화면이 로그아웃 때 그 회원의 브라우저 임시 글 키({@code draft:{memberId}:*})를 지우는 데
 *     쓴다(FR-041, 002 {@code clearMemberDrafts})
 * @param profileImageUrl 현재 프로필 사진(작은 사진 = {@code ProfileImageKeys.display()}) 주소. 없으면 null → 화면이
 *     기본 아바타를 그린다
 * @param restoreDeadline 탈퇴 유예 회원의 복구 기한({@code withdrawn_at + grace}), 그 밖에는 null (015 R6)
 * @param restoreExpired 탈퇴 유예 회원이고 지금이 복구 기한보다 뒤면 true (015 FR-021a)
 */
public record MeSummary(
        long memberId,
        String handle,
        String nickname,
        String role,
        String status,
        String provider,
        boolean emailVerified,
        boolean reagreementRequired,
        String profileImageUrl,
        java.time.Instant restoreDeadline,
        boolean restoreExpired) {}
