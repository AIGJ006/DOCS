package com.team.blog.account.application;

import com.team.blog.account.domain.Provider;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/**
 * 소셜 가입 대기 정보 (R-07, FR-030) — 세션 속성 {@code pendingSocialSignup}. 마무리 전에는 계정을 만들지 않고 이것만 10분
 * ({@code blog.auth.social.pending-ttl}) 보관한다. 이 동안 SecurityContext는 익명이다. 세션(Redis)에 JDK 직렬화로
 * 저장된다.
 */
public record PendingSocialSignup(
        Provider provider,
        String providerUserId,
        String email,
        boolean emailVerified,
        String displayName,
        String pictureUrl,
        Instant createdAt)
        implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    public static PendingSocialSignup of(SocialProfile profile, Instant now) {
        return new PendingSocialSignup(
                profile.provider(),
                profile.providerUserId(),
                profile.email(),
                profile.emailVerified() && profile.email() != null,
                profile.displayName(),
                profile.pictureUrl(),
                now);
    }

    /** GitHub에 확인된 대표 이메일이 없어 마무리 화면에서 이메일을 받아야 한다. */
    public boolean emailRequired() {
        return !emailVerified || email == null;
    }

    public PendingSocialSignup withCreatedAt(Instant value) {
        return new PendingSocialSignup(
                provider, providerUserId, email, emailVerified, displayName, pictureUrl, value);
    }
}
