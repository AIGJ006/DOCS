package com.team.blog.account.infra.security;

import com.team.blog.account.application.AccountReasonCode;
import com.team.blog.shared.error.ErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Set;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 약관 재동의 전 차단 (FR-012, SC-011, R-24). 세션에 {@link LoginSession#REAGREEMENT_REQUIRED}가 있으면 허용 목록 밖
 * {@code /api/**} 요청(GET 포함)을 403 {@code REAGREEMENT_REQUIRED}로 막는다. 화면 셸·정적 파일({@code /api} 밖)은
 * 그대로 통과한다 — 화면은 {@code /reagree}·{@code /terms}·{@code /privacy}만 보여 준다.
 *
 * <p>허용 목록: {@code GET /api/me}, {@code GET /api/agreements/current}, {@code PUT
 * /api/me/agreements}, {@code POST /api/auth/logout}, {@code GET /api/auth/csrf}.
 *
 * <p>Bean이 아니다 — {@link WithdrawnAccountGateCustomizer}가 {@link WithdrawnAccountGateFilter} 바로 뒤에
 * 붙인다(탈퇴 유예 안내가 먼저).
 */
public class ReagreementGateFilter extends OncePerRequestFilter {

    private static final Set<String> ALLOWED =
            Set.of(
                    "GET /api/me",
                    "GET /api/agreements/current",
                    "PUT /api/me/agreements",
                    "POST /api/auth/logout",
                    "GET /api/auth/csrf");

    private final ErrorResponseWriter errorWriter;

    public ReagreementGateFilter(ErrorResponseWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = path(request);
        return !(path.equals("/api") || path.startsWith("/api/"));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null
                && Boolean.TRUE.equals(session.getAttribute(LoginSession.REAGREEMENT_REQUIRED))
                && !ALLOWED.contains(request.getMethod() + " " + path(request))) {
            errorWriter.write(response, AccountReasonCode.REAGREEMENT_REQUIRED);
            return;
        }
        chain.doFilter(request, response);
    }

    private static String path(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        return context != null && !context.isEmpty() && uri.startsWith(context)
                ? uri.substring(context.length())
                : uri;
    }
}
