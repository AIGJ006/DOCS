package com.team.blog.account.web.dto;

/** 비밀번호 변경 (contracts {@code changeMyPassword}). 비밀번호는 {@link #toString()}에 나오지 않는다. */
public record PasswordChangeRequest(
        String currentPassword, String newPassword, String newPasswordConfirm) {

    @Override
    public String toString() {
        return "PasswordChangeRequest[***]";
    }
}
