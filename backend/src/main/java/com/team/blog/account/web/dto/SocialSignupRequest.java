package com.team.blog.account.web.dto;

import com.team.blog.account.application.SocialSignupCommand;

/**
 * 소셜 가입 마무리 요청 (contracts {@code SocialSignupRequest}). {@code handleBody}는 접두어를 뺀 본문, {@code
 * email}은 {@code emailRequired}일 때만 쓴다. {@code useProfilePhoto} 기본값은 true.
 */
public record SocialSignupRequest(
        String handleBody,
        String nickname,
        String email,
        Boolean useProfilePhoto,
        AgreementConsent agreements) {

    public SocialSignupCommand toCommand() {
        return new SocialSignupCommand(
                handleBody,
                nickname,
                email,
                useProfilePhoto == null || useProfilePhoto,
                agreements == null ? null : agreements.toVersions());
    }
}
