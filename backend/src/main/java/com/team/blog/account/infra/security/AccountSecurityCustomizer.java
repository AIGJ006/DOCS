package com.team.blog.account.infra.security;

import com.team.blog.shared.security.SecurityFilterChainCustomizer;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * account의 로그인·로그아웃을 공통 보안 필터 체인에 붙인다 (R-04, FR-034·036·041).
 *
 * <ul>
 *   <li>이메일 로그인: 폼 로그인 {@code POST /api/auth/login}({@code email}, {@code password},
 *       form-urlencoded, CSRF 필요). 성공·실패는 JSON 처리기. 화면 경로 {@code /login}은 React가 그리므로 기본 로그인 페이지를
 *       만들지 않도록 {@code loginPage("/login")}로 둔다(비로그인 401은 공통 {@code LoginRequiredEntryPoint}가 그대로
 *       맡는다).
 *   <li>로그아웃: {@code POST /api/auth/logout} → 서버 세션 삭제 + 204(이미 로그아웃이어도 204).
 * </ul>
 *
 * 소셜 로그인({@code oauth2Login})은 US2, 요청 제한·재동의 필터는 US5에서 여기에 더한다.
 */
@Component
@Order(0)
public class AccountSecurityCustomizer implements SecurityFilterChainCustomizer {

    public static final String LOGIN_URL = "/api/auth/login";
    public static final String LOGOUT_URL = "/api/auth/logout";

    private final JsonLoginSuccessHandler successHandler;
    private final JsonLoginFailureHandler failureHandler;

    public AccountSecurityCustomizer(
            JsonLoginSuccessHandler successHandler, JsonLoginFailureHandler failureHandler) {
        this.successHandler = successHandler;
        this.failureHandler = failureHandler;
    }

    @Override
    public void customize(HttpSecurity http) throws Exception {
        http.formLogin(
                form ->
                        form.loginPage("/login")
                                .loginProcessingUrl(LOGIN_URL)
                                .usernameParameter("email")
                                .passwordParameter("password")
                                .successHandler(successHandler)
                                .failureHandler(failureHandler));
        http.logout(
                logout ->
                        logout.logoutUrl(LOGOUT_URL)
                                .invalidateHttpSession(true)
                                .clearAuthentication(true)
                                .logoutSuccessHandler(
                                        new HttpStatusReturningLogoutSuccessHandler(
                                                HttpStatus.NO_CONTENT)));
    }
}
