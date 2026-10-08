package com.team.blog.account.infra.security;

import java.util.List;
import java.util.Map;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;

/** GitHub API 호출 ({@code GET /user}, {@code GET /user/emails}, R-06). 테스트는 가짜 Bean으로 바꾼다(R-35). */
public interface GitHubUserClient {

    /** {@code GET /user} 응답 속성 (숫자 {@code id}, {@code login}, {@code name}, {@code avatar_url}). */
    Map<String, Object> user(OAuth2UserRequest request);

    /** {@code GET /user/emails} ({@code user:email} 범위). */
    List<GitHubEmail> emails(OAuth2UserRequest request);

    record GitHubEmail(String email, boolean primary, boolean verified) {}
}
