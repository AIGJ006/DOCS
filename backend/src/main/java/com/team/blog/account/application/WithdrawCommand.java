package com.team.blog.account.application;

/**
 * 탈퇴 신청 입력 (015 data-model §3-2). 사유는 받지 않는다(FR-004). 방법에 맞지 않는 칸은 무시한다.
 *
 * @param confirmed 안내 확인 체크 ({@code true}가 아니면 400 {@code WITHDRAW_CONFIRM_REQUIRED})
 * @param password 이메일 가입 회원의 현재 비밀번호
 * @param confirmText 소셜 가입 회원의 확인 문구
 */
public record WithdrawCommand(Boolean confirmed, String password, String confirmText) {

    public boolean isConfirmed() {
        return Boolean.TRUE.equals(confirmed);
    }

    @Override
    public String toString() {
        return "WithdrawCommand[confirmed=" + confirmed + ", ***]";
    }
}
