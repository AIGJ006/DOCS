package com.team.blog.account.web.dto;

import com.team.blog.account.application.SocialSignupDraft;
import java.time.Instant;

/** 소셜 가입 마무리 화면의 미리 채운 값 (contracts {@code SocialSignupDraft}). 없는 값은 null로 싣는다. */
public record SocialSignupDraftResponse(
        String provider,
        String handlePrefix,
        String suggestedHandleBody,
        String suggestedNickname,
        String email,
        boolean emailRequired,
        String profilePhotoUrl,
        boolean existingAccountNotice,
        Instant expiresAt) {

    public static SocialSignupDraftResponse from(SocialSignupDraft draft) {
        return new SocialSignupDraftResponse(
                draft.provider().name(),
                draft.handlePrefix(),
                draft.suggestedHandleBody(),
                draft.suggestedNickname(),
                draft.email(),
                draft.emailRequired(),
                draft.profilePhotoUrl(),
                draft.existingAccountNotice(),
                draft.expiresAt());
    }
}
