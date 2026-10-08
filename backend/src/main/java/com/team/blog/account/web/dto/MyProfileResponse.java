package com.team.blog.account.web.dto;

import com.team.blog.account.application.MyProfile;
import java.time.Instant;

/** openapi {@code MyProfile}. null 칸도 그대로 내보낸다(필수 칸). */
public record MyProfileResponse(
        String handle,
        String nickname,
        String bio,
        Long profileImageId,
        String profileImageUrl,
        Instant nicknameChangeAvailableAt) {

    public static MyProfileResponse of(MyProfile profile) {
        return new MyProfileResponse(
                profile.handle(),
                profile.nickname(),
                profile.bio(),
                profile.profileImageId(),
                profile.profileImageUrl(),
                profile.nicknameChangeAvailableAt());
    }
}
