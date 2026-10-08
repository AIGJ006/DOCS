package com.team.blog.account.application;

import com.team.blog.account.domain.Provider;
import java.time.Instant;

/** 소셜 가입 마무리 화면의 미리 채운 값 (openapi {@code SocialSignupDraft}). */
public record SocialSignupDraft(
        Provider provider,
        String handlePrefix,
        String suggestedHandleBody,
        String suggestedNickname,
        String email,
        boolean emailRequired,
        String profilePhotoUrl,
        boolean existingAccountNotice,
        Instant expiresAt) {}
