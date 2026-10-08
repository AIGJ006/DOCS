package com.team.blog.shared.security;

import com.team.blog.shared.error.ErrorResponseWriter;
import com.team.blog.shared.web.NotFoundPageRenderer;
import java.util.LinkedHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.RequestMatcherDelegatingAccessDeniedHandler;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;

/**
 * 관리자 경로 규칙 (004 T066, FR-044, research R-13·R-22). 001 확장 지점 {@link
 * SecurityFilterChainCustomizer}로 붙인다 — 001 {@code SecurityConfig}는 고치지 않는다.
 *
 * <ul>
 *   <li>{@code /admin/**}·{@code /api/admin/**} → 관리자만(DB 역할, {@link AdminPaths#isAdmin}). 다른 기능의
 *       규칙보다 먼저 두려고 가장 앞 순서({@code @Order(HIGHEST_PRECEDENCE)})다. 인가 필터가 컨트롤러 경로 확인보다 먼저 돌아 있는
 *       주소·없는 주소가 같은 응답이다.
 *   <li>예외 처리: 001이 등록한 진입점({@code LOGIN_REQUIRED})·거부 처리기({@code CSRF_REJECTED})를 그대로 두고, 관리자
 *       경로에서만 {@link AdminPathAuthenticationEntryPoint}·{@link AdminPathAccessDeniedHandler}로 바꾼다.
 * </ul>
 *
 * <p>(구현 메모) 001 {@code SecurityConfig}가 진입점·거부 처리기를 직접 지정해 Spring의 경로별 기본값({@code
 * defaultAuthenticationEntryPointFor} 등)은 쓰이지 않는다. 그래서 같은 001 처리기를 기본값으로 둔 경로별 위임 처리기로 다시 지정한다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AdminPathSecurityCustomizer implements SecurityFilterChainCustomizer {

    private final AdminPaths adminPaths;
    private final ErrorResponseWriter writer;
    private final NotFoundPageRenderer notFoundPage;
    private final Resource shell;

    public AdminPathSecurityCustomizer(
            AdminPaths adminPaths,
            ErrorResponseWriter writer,
            NotFoundPageRenderer notFoundPage,
            @Value("classpath:static/index.html") Resource shell) {
        this.adminPaths = adminPaths;
        this.writer = writer;
        this.notFoundPage = notFoundPage;
        this.shell = shell;
    }

    @Override
    public void customize(HttpSecurity http) throws Exception {
        AuthenticationEntryPoint commonEntry = new LoginRequiredEntryPoint(writer);
        AccessDeniedHandler commonDenied = new CsrfAccessDeniedHandler(writer);
        AuthenticationEntryPoint adminEntry = new AdminPathAuthenticationEntryPoint(writer, shell);
        AccessDeniedHandler adminDenied =
                new AdminPathAccessDeniedHandler(
                        adminPaths, writer, notFoundPage, adminEntry, commonDenied);

        LinkedHashMap<RequestMatcher, AuthenticationEntryPoint> entries = new LinkedHashMap<>();
        entries.put(AdminPaths.ALL, adminEntry);
        DelegatingAuthenticationEntryPoint entryPoint =
                new DelegatingAuthenticationEntryPoint(entries);
        entryPoint.setDefaultEntryPoint(commonEntry);

        LinkedHashMap<RequestMatcher, AccessDeniedHandler> denied = new LinkedHashMap<>();
        denied.put(AdminPaths.ALL, adminDenied);

        AuthorizationManager<RequestAuthorizationContext> adminOnly =
                (authentication, context) ->
                        new AuthorizationDecision(
                                adminPaths.isAdmin(authentication.get(), context.getRequest()));

        http.exceptionHandling(
                        exceptions ->
                                exceptions
                                        .authenticationEntryPoint(entryPoint)
                                        .accessDeniedHandler(
                                                new RequestMatcherDelegatingAccessDeniedHandler(
                                                        denied, commonDenied)))
                .authorizeHttpRequests(
                        authorize -> authorize.requestMatchers(AdminPaths.ALL).access(adminOnly));
    }
}
