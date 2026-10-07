package com.team.blog.account.web.dto;

import com.team.blog.account.application.EmailSignupCommand;

/**
 * 이메일 가입 요청 (contracts {@code EmailSignupRequest}). 칸 검사는 Service가 모든 칸을 한 번에 해 오류를 모아 돌려주므로 여기에는
 * Bean Validation을 두지 않는다. 비밀번호는 {@link #toString()}에 나오지 않는다(FR-015).
 */
public record EmailSignupRequest(
        String email,
        String handle,
        String password,
        String passwordConfirm,
        String nickname,
        AgreementConsent agreements) {

    public EmailSignupCommand toCommand() {
        return new EmailSignupCommand(
                email,
                handle,
                password,
                passwordConfirm,
                nickname,
                agreements == null ? null : agreements.toVersions());
    }

    @Override
    public String toString() {
        return "EmailSignupRequest[handle=" + handle + ", nickname=" + nickname + "]";
    }
}
