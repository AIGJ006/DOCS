package com.team.blog.account.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.team.blog.account.application.SettingsUpdate;

/** 계정 설정 변경 (openapi {@code SettingsUpdateRequest}). 보낸 칸만 바꾼다. 알 수 없는 필드는 무시한다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SettingsUpdateRequest {

    private boolean defaultVisibilityPresent;
    private String defaultVisibility;
    private boolean lastActiveVisiblePresent;
    private Boolean lastActiveVisible;

    @JsonSetter("defaultVisibility")
    public void setDefaultVisibility(String defaultVisibility) {
        this.defaultVisibilityPresent = true;
        this.defaultVisibility = defaultVisibility;
    }

    @JsonSetter("lastActiveVisible")
    public void setLastActiveVisible(Boolean lastActiveVisible) {
        this.lastActiveVisiblePresent = true;
        this.lastActiveVisible = lastActiveVisible;
    }

    public SettingsUpdate toCommand() {
        return new SettingsUpdate(
                defaultVisibilityPresent,
                defaultVisibility,
                lastActiveVisiblePresent,
                lastActiveVisible);
    }
}
