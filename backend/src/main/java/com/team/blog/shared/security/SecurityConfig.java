package com.team.blog.shared.security;

import com.team.blog.shared.error.ErrorResponseWriter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;

/**
 * 공통 보안 설정 (02 §5, R-05). 세션 쿠키(Spring Session + Redis) + CSRF 토큰. 공통 코드는 JWT를 쓰지 않는다.
 *
 * <ul>
 *   <li>CSRF: {@code XSRF-TOKEN} 쿠키(HttpOnly 아님, SameSite=Lax, Secure, Path=/) + {@code
 *       X-XSRF-TOKEN} 헤더. 앱 첫 진입 때 {@code GET /api/auth/csrf}로 쿠키를 받는다.
 *   <li>세션 고정 방지: 로그인 때 세션 ID 변경. 폼 로그인·HTTP Basic·기본 로그아웃은 끈다(account가 확장 지점으로 붙인다).
 *   <li>비로그인 → 401 {@code LOGIN_REQUIRED} JSON, CSRF 실패 → 403 {@code CSRF_REJECTED}.
 *   <li>인가: {@code /api/me/**}만 로그인 필요, 나머지는 허용. 로그인 필요 여부·계정 상태는 {@link LoginRequired}와
 *       Service({@link AccountStatusGuard})가 판단한다(42 §3 ①②).
 *   <li>요청 캐시를 쓰지 않는다(401 때 세션을 만들지 않음 — Redis 장애 시 비로그인 읽기를 지키기 위해).
 *   <li>확장: {@link SecurityFilterChainCustomizer} Bean을 {@code @Order} 순서로 공통 인가 규칙보다 먼저 적용한다.
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    public static final String CSRF_COOKIE_NAME = "XSRF-TOKEN";
    public static final String CSRF_HEADER_NAME = "X-XSRF-TOKEN";

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectProvider<SecurityFilterChainCustomizer> customizers,
            ErrorResponseWriter errorResponseWriter)
            throws Exception {
        http.csrf(
                        csrf ->
                                csrf.csrfTokenRepository(csrfTokenRepository())
                                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                .sessionManagement(
                        session -> session.sessionFixation(fixation -> fixation.changeSessionId()))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .exceptionHandling(
                        exceptions ->
                                exceptions
                                        .authenticationEntryPoint(
                                                new LoginRequiredEntryPoint(errorResponseWriter))
                                        .accessDeniedHandler(
                                                new CsrfAccessDeniedHandler(errorResponseWriter)));

        for (SecurityFilterChainCustomizer customizer : customizers.orderedStream().toList()) {
            customizer.customize(http);
        }

        http.authorizeHttpRequests(
                authorize ->
                        authorize
                                .requestMatchers("/api/me/**")
                                .authenticated()
                                .anyRequest()
                                .permitAll());
        return http.build();
    }

    /** 비밀번호 해시: BCrypt 비용 10 (R-14). */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    private static CookieCsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieName(CSRF_COOKIE_NAME);
        repository.setHeaderName(CSRF_HEADER_NAME);
        repository.setCookiePath("/");
        repository.setCookieCustomizer(cookie -> cookie.sameSite("Lax").secure(true));
        return repository;
    }
}
