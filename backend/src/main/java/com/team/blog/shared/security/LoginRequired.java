package com.team.blog.shared.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 로그인이 필요한 핸들러(메서드 또는 컨트롤러 전체). 비로그인 요청은 401 {@code LOGIN_REQUIRED}. {@code /api/me/**}는
 * SecurityConfig가 이미 막으므로 그 밖의 경로(예: {@code PUT /api/posts/{postId}/like})에 붙인다.
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface LoginRequired {}
