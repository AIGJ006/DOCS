package com.team.blog.account.application;

/**
 * 다른 모듈이 작성자·대상 회원을 그릴 때 쓰는 표시 정보 (007 data-model §5).
 *
 * @param id 회원 번호
 * @param handle 블로그 주소 (익명 처리된 회원은 {@code null})
 * @param nickname 닉네임 (익명 처리된 회원은 {@code null})
 * @param withdrawn 탈퇴 유예({@code status = WITHDRAWN}) 또는 익명 처리({@code deleted_at}) — 화면은 "탈퇴한 사용자"
 */
public record MemberDisplay(long id, String handle, String nickname, boolean withdrawn) {}
