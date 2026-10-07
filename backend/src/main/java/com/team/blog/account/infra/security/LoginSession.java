package com.team.blog.account.infra.security;

import com.team.blog.account.application.LoginOutcome;
import com.team.blog.account.domain.Provider;
import jakarta.servlet.http.HttpSession;

/**
 * 로그인 세션 속성 (data-model §3). {@code previousLoginAt}(갱신 전 {@code last_login_at}, 없으면 속성 없음 = "첫
 * 로그인")과 이번 로그인 방식 {@code provider}. 본인 계정 화면(US8)이 읽는다.
 */
public final class LoginSession {

    public static final String PREVIOUS_LOGIN_AT = "previousLoginAt";
    public static final String PROVIDER = "provider";

    private LoginSession() {}

    static void record(HttpSession session, Provider provider, LoginOutcome outcome) {
        session.setAttribute(PREVIOUS_LOGIN_AT, outcome.previousLoginAt());
        session.setAttribute(PROVIDER, provider.name());
    }
}
