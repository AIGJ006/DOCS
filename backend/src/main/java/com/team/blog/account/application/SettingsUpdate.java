package com.team.blog.account.application;

/** 계정 설정 변경 요청 (openapi {@code SettingsUpdateRequest}). 보낸 칸만 {@code *Present = true}. */
public record SettingsUpdate(
        boolean defaultVisibilityPresent,
        String defaultVisibility,
        boolean lastActiveVisiblePresent,
        Boolean lastActiveVisible) {

    public boolean isEmpty() {
        return !defaultVisibilityPresent && !lastActiveVisiblePresent;
    }
}
