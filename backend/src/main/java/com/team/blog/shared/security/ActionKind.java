package com.team.blog.shared.security;

/**
 * {@link AccountStatusGuard}가 판정하는 행동 종류 (FR-007, 42 §9, data-model §4-1 표).
 *
 * <table>
 *   <caption>상태 × 행동</caption>
 *   <tr><th></th><th>CONTENT_WRITE</th><th>ACCOUNT_WRITE</th><th>CONTENT_CLEANUP</th></tr>
 *   <tr><td>ACTIVE + 인증 전</td><td>403 EMAIL_NOT_VERIFIED</td><td>통과</td><td>통과</td></tr>
 *   <tr><td>ACTIVE + 인증</td><td>통과</td><td>통과</td><td>통과</td></tr>
 *   <tr><td>SUSPENDED</td><td colspan="3">403 ACCOUNT_SUSPENDED</td></tr>
 *   <tr><td>WITHDRAWN(유예)</td><td colspan="3">403 ACCOUNT_WITHDRAWN</td></tr>
 * </table>
 */
public enum ActionKind {
    /** 글쓰기(새 글·저장·발행·공개 범위 변경)·댓글·사진 업로드·좋아요·신고. 이메일 인증이 필요하다. */
    CONTENT_WRITE,
    /** 닉네임·소개, 비밀번호 변경, 기본 공개 범위, 탈퇴, 친구 요청. 인증 전도 통과. */
    ACCOUNT_WRITE,
    /** 자기 글·댓글 삭제·복구·영구 삭제와 내 글 관리 목록(006·007). 인증 전도 통과. */
    CONTENT_CLEANUP;

    /** 이메일 인증 전 회원에게 막는 행동인가. */
    public boolean requiresVerifiedEmail() {
        return this == CONTENT_WRITE;
    }
}
