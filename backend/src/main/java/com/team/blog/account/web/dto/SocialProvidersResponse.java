package com.team.blog.account.web.dto;

import java.util.List;

/** 로그인 화면에 버튼을 보일 소셜 로그인 수단 (앱 키가 설정된 것만, {@code GOOGLE}·{@code GITHUB}). */
public record SocialProvidersResponse(List<String> providers) {}
