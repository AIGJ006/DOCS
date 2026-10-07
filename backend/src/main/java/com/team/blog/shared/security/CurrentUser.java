package com.team.blog.shared.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러 파라미터에 현재 로그인 회원 번호를 넣는다. 세션(SecurityContext)에서만 꺼내며 요청 파라미터·본문의 회원 번호는 쓰지 않는다 (constitution
 * III).
 *
 * <ul>
 *   <li>{@code @CurrentUser Long memberId} — 비로그인이면 401 {@code LOGIN_REQUIRED}.
 *   <li>{@code @CurrentUser(required = false) Optional<Long> memberId} — 비로그인이면 빈 값.
 * </ul>
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {

    boolean required() default true;
}
