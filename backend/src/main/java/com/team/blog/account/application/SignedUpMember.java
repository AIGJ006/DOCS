package com.team.blog.account.application;

import com.team.blog.account.domain.Provider;
import com.team.blog.account.domain.Role;

/**
 * 가입 결과. 웹 응답({@code SignupResult})은 {@code handle}·{@code nickname}·{@code emailVerified}만 내보낸다.
 */
public record SignedUpMember(
        long memberId,
        String handle,
        String nickname,
        boolean emailVerified,
        Role role,
        Provider provider) {}
