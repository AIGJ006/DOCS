package com.team.blog.account.application;

/**
 * 소셜 가입 마무리 입력 (openapi {@code SocialSignupRequest}).
 *
 * @param handleBody 접두어를 뺀 주소 본문 — 서버가 가입 수단 접두어를 붙인다
 * @param email {@code emailRequired}일 때만 쓴다
 */
public record SocialSignupCommand(
        String handleBody,
        String nickname,
        String email,
        boolean useProfilePhoto,
        AgreementVersions agreements) {}
