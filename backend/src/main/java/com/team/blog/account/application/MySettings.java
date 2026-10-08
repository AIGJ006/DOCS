package com.team.blog.account.application;

import com.team.blog.account.domain.PreviousLogin;

/**
 * 내 계정 설정 (openapi {@code MySettings}, FR-046·053·058·061).
 *
 * @param email 읽기 전용 (GitHub 이메일 없는 계정 등은 null)
 * @param previousLogin null이면 "첫 로그인"
 * @param passwordChangeAvailable LOCAL만 true
 */
public record MySettings(
        String email,
        String provider,
        PreviousLogin previousLogin,
        String defaultVisibility,
        boolean lastActiveVisible,
        boolean passwordChangeAvailable) {}
