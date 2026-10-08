package com.team.blog.shared.security;

import com.team.blog.shared.error.CommonReasonCode;
import com.team.blog.shared.error.ErrorResponseWriter;
import com.team.blog.shared.web.CacheControlPolicy;
import com.team.blog.shared.web.NotFoundPageRenderer;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * 관리자 경로의 거부 처리기 (004 T064, FR-044, SC-007). 관리자가 아닌 로그인 회원에게 403 대신 공통 404를 준다 — 관리자 기능이 있는지 드러나지
 * 않는다.
 *
 * <ul>
 *   <li>로그인한 일반 회원(인증 전·정지 포함): {@code /api/admin/**} = JSON {@code NOT_FOUND} + {@code
 *       Cache-Control: private, no-store}, {@code /admin/**} = {@link NotFoundPageRenderer}. CSRF
 *       토큰이 없어도 같다.
 *   <li>비회원(CSRF 실패로 여기 온 쓰기 포함): 비회원 진입점의 401 — GET과 POST가 같은 응답.
 *   <li>관리자: 관리자 경로가 아닐 때와 같이 001 처리기({@code CSRF_REJECTED})에 맡긴다.
 * </ul>
 */
public class AdminPathAccessDeniedHandler implements AccessDeniedHandler {

    private final AdminPaths adminPaths;
    private final ErrorResponseWriter writer;
    private final NotFoundPageRenderer notFoundPage;
    private final AuthenticationEntryPoint anonymous;
    private final AccessDeniedHandler fallback;

    public AdminPathAccessDeniedHandler(
            AdminPaths adminPaths,
            ErrorResponseWriter writer,
            NotFoundPageRenderer notFoundPage,
            AuthenticationEntryPoint anonymous,
            AccessDeniedHandler fallback) {
        this.adminPaths = adminPaths;
        this.writer = writer;
        this.notFoundPage = notFoundPage;
        this.anonymous = anonymous;
        this.fallback = fallback;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException)
            throws IOException, ServletException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!AdminPaths.isMember(authentication)) {
            anonymous.commence(
                    request, response, new InsufficientAuthenticationException("관리자 경로: 로그인 필요"));
            return;
        }
        if (adminPaths.isAdmin(authentication, request)) {
            fallback.handle(request, response, accessDeniedException);
            return;
        }
        if (AdminPaths.PAGES.matches(request)) {
            writePage(response);
            return;
        }
        response.setHeader(HttpHeaders.CACHE_CONTROL, CacheControlPolicy.NO_STORE);
        writer.write(response, CommonReasonCode.NOT_FOUND);
    }

    private void writePage(HttpServletResponse response) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        ResponseEntity<byte[]> page = notFoundPage.render();
        response.setStatus(page.getStatusCode().value());
        page.getHeaders()
                .forEach((name, values) -> values.forEach(v -> response.addHeader(name, v)));
        byte[] body = page.getBody();
        response.setContentLength(body.length);
        response.getOutputStream().write(body);
        response.flushBuffer();
    }
}
