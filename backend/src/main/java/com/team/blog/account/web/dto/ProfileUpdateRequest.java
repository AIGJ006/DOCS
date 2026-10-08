package com.team.blog.account.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.team.blog.account.application.ProfileUpdate;

/**
 * 프로필 저장 (openapi {@code ProfileUpdateRequest}). 칸을 보내지 않은 것과 {@code null}을 보낸 것을 구분한다({@code
 * profileImageId: null} = 기본 이미지로, {@code bio: null} = 소개 비우기). 알 수 없는 필드(예 {@code memberId}·{@code
 * handle})는 무시한다(FR-047).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProfileUpdateRequest {

    private boolean nicknamePresent;
    private String nickname;
    private boolean bioPresent;
    private String bio;
    private boolean profileImagePresent;
    private Long profileImageId;

    @JsonSetter("nickname")
    public void setNickname(String nickname) {
        this.nicknamePresent = true;
        this.nickname = nickname;
    }

    @JsonSetter("bio")
    public void setBio(String bio) {
        this.bioPresent = true;
        this.bio = bio;
    }

    @JsonSetter("profileImageId")
    public void setProfileImageId(Long profileImageId) {
        this.profileImagePresent = true;
        this.profileImageId = profileImageId;
    }

    public ProfileUpdate toCommand() {
        return new ProfileUpdate(
                nicknamePresent && nickname != null,
                nickname,
                bioPresent,
                bio,
                profileImagePresent,
                profileImageId);
    }
}
