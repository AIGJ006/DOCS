package com.team.blog.shared.security;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;

/**
 * 공통 {@code SecurityFilterChain}의 확장 지점. 기능 모듈은 SecurityConfig를 고치지 않고 이 인터페이스를 구현한 Bean으로 규칙·필터를
 * 붙인다(예: account의 폼·소셜 로그인과 필터, 004의 {@code /admin/**} 규칙).
 *
 * <ul>
 *   <li>{@code @Order} 순서대로 모두 적용한다(작은 값이 먼저).
 *   <li>공통 인가 규칙({@code /api/me/**} 로그인 필요, 나머지 허용)보다 <b>먼저</b> 적용된다. 그래서 여기서 더한 {@code
 *       authorizeHttpRequests} 규칙이 공통 규칙보다 앞선다. {@code anyRequest()}는 쓰지 않는다(공통 규칙이 마지막에 둔다).
 * </ul>
 */
@FunctionalInterface
public interface SecurityFilterChainCustomizer {

    void customize(HttpSecurity http) throws Exception;
}
