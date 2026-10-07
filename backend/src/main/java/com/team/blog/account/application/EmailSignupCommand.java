package com.team.blog.account.application;

/**
 * 이메일 가입 입력 (contracts {@code EmailSignupRequest}). 값은 받은 그대로이며 정규화·검사는 {@link SignupService}가 한다.
 * 비밀번호는 {@link #toString()}에 나오지 않는다(FR-015).
 */
public record EmailSignupCommand(
        String email,
        String handle,
        String password,
        String passwordConfirm,
        String nickname,
        AgreementVersions agreements) {

    @Override
    public String toString() {
        return "EmailSignupCommand[handle=" + handle + ", nickname=" + nickname + "]";
    }
}
