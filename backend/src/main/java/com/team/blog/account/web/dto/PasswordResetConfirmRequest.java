package com.team.blog.account.web.dto;

/**
 * 재설정 링크로 새 비밀번호 저장 (contracts {@code confirmPasswordReset}). 비밀번호는 {@link #toString()}에 나오지 않는다.
 */
public record PasswordResetConfirmRequest(
        String token, String newPassword, String newPasswordConfirm) {

    @Override
    public String toString() {
        return "PasswordResetConfirmRequest[***]";
    }
}
