package com.team.blog.account.application;

import com.team.blog.account.domain.Role;

/** 소셜 콜백 판정 결과: 연결된 계정으로 로그인했거나, 처음이라 가입 대기 정보를 만들었다. */
public sealed interface SocialLoginResult {

    record LoggedIn(long memberId, Role role, LoginOutcome outcome) implements SocialLoginResult {}

    record PendingSignup(PendingSocialSignup pending) implements SocialLoginResult {}
}
