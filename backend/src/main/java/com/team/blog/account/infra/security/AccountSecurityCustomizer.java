package com.team.blog.account.infra.security;

import com.team.blog.account.infra.AccountProperties;
import com.team.blog.account.infra.redis.LoginFailureCounter;
import com.team.blog.shared.error.ErrorResponseWriter;
import com.team.blog.shared.infra.ratelimit.RateLimiter;
import com.team.blog.shared.infra.redis.RedisGuard;
import com.team.blog.shared.security.SecurityFilterChainCustomizer;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
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
 * <ul>
 *   <li>소셜 로그인({@code oauth2Login}, US2): 시작 {@code GET
 *       /oauth2/authorization/{google|github}?redirect=…} (redirect는 세션 {@code loginRedirect}에 보관),
 *       콜백 {@code /login/oauth2/code/{id}}. 사용자 정보는 {@link OidcMemberUserService}·{@link
 *       OAuth2MemberUserService}, 결과 처리는 {@link OAuth2LoginSuccessHandler}·{@link
 *       OAuth2LoginFailureHandler}. 액세스 토큰은 보관하지 않는다.
 * </ul>
 *
 * <ul>
 *   <li>로그인 요청 제한({@link LoginRateLimitFilter}, US5): 폼 로그인 필터 앞 — Redis 장애 503, IP 1분 20회, 이메일 잠금.
 * </ul>
 *
 * 재동의 게이트({@link ReagreementGateFilter})는 탈퇴 유예 게이트 뒤에 와야 하므로 {@link
 * WithdrawnAccountGateCustomizer}가 붙인다.
 */
@Component
@Order(0)
public class AccountSecurityCustomizer implements SecurityFilterChainCustomizer {

    public static final String LOGIN_URL = "/api/auth/login";
    public static final String LOGOUT_URL = "/api/auth/logout";

    private final JsonLoginSuccessHandler successHandler;
    private final JsonLoginFailureHandler failureHandler;
    private final SocialClientRegistrations socialRegistrations;
    private final SafeRedirectResolver safeRedirect;
    private final OidcMemberUserService oidcUserService;
    private final OAuth2MemberUserService oauth2UserService;
    private final OAuth2LoginSuccessHandler socialSuccessHandler;
    private final OAuth2LoginFailureHandler socialFailureHandler;
    private final LoginRateLimitFilter loginRateLimitFilter;

    public AccountSecurityCustomizer(
            JsonLoginSuccessHandler successHandler,
            JsonLoginFailureHandler failureHandler,
            SocialClientRegistrations socialRegistrations,
            SafeRedirectResolver safeRedirect,
            OidcMemberUserService oidcUserService,
            OAuth2MemberUserService oauth2UserService,
            OAuth2LoginSuccessHandler socialSuccessHandler,
            OAuth2LoginFailureHandler socialFailureHandler,
            RedisGuard redisGuard,
            RateLimiter rateLimiter,
            LoginFailureCounter loginFailureCounter,
            ErrorResponseWriter errorWriter,
            AccountProperties properties) {
        this.successHandler = successHandler;
        this.failureHandler = failureHandler;
        this.socialRegistrations = socialRegistrations;
        this.safeRedirect = safeRedirect;
        this.oidcUserService = oidcUserService;
        this.oauth2UserService = oauth2UserService;
        this.socialSuccessHandler = socialSuccessHandler;
        this.socialFailureHandler = socialFailureHandler;
        this.loginRateLimitFilter =
                new LoginRateLimitFilter(
                        redisGuard, rateLimiter, loginFailureCounter, errorWriter, properties);
    }

    @Override
    public void customize(HttpSecurity http) throws Exception {
        http.addFilterBefore(loginRateLimitFilter, UsernamePasswordAuthenticationFilter.class);
        http.formLogin(
                form ->
                        form.loginPage("/login")
                                .loginProcessingUrl(LOGIN_URL)
                                .usernameParameter("email")
                                .passwordParameter("password")
                                .successHandler(successHandler)
                                .failureHandler(failureHandler));
        http.oauth2Login(
                oauth ->
                        oauth.loginPage("/login")
                                .clientRegistrationRepository(socialRegistrations)
                                .authorizedClientRepository(new NoStoreAuthorizedClientRepository())
                                .authorizationEndpoint(
                                        endpoint ->
                                                endpoint.authorizationRequestResolver(
                                                        new RedirectSavingAuthorizationRequestResolver(
                                                                socialRegistrations, safeRedirect)))
                                .userInfoEndpoint(
                                        userInfo ->
                                                userInfo.oidcUserService(oidcUserService)
                                                        .userService(oauth2UserService))
                                .successHandler(socialSuccessHandler)
                                .failureHandler(socialFailureHandler));
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
