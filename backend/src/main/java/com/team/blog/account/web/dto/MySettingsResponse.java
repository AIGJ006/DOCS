package com.team.blog.account.web.dto;

import com.team.blog.account.application.MySettings;
import java.time.Instant;

/** openapi {@code MySettings}. {@code previousLogin}이 null이면 "첫 로그인". */
public record MySettingsResponse(
        String email,
        String provider,
        PreviousLoginResponse previousLogin,
        String defaultVisibility,
        boolean lastActiveVisible,
        boolean passwordChangeAvailable) {

    public record PreviousLoginResponse(Instant at, String provider) {}

    public static MySettingsResponse of(MySettings settings) {
        return new MySettingsResponse(
                settings.email(),
                settings.provider(),
                settings.previousLogin() == null
                        ? null
                        : new PreviousLoginResponse(
                                settings.previousLogin().at(), settings.previousLogin().provider()),
                settings.defaultVisibility(),
                settings.lastActiveVisible(),
                settings.passwordChangeAvailable());
    }
}
