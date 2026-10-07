package com.team.blog.account.infra.security;

import com.team.blog.account.application.AccountStatusGuardService;
import com.team.blog.account.application.MemberAccessInfo;
import com.team.blog.account.application.MemberQueryService;
import com.team.blog.account.domain.MemberStatus;
import com.team.blog.shared.error.AccountStateException;
import com.team.blog.shared.error.CommonReasonCode;
import com.team.blog.shared.error.ErrorResponseWriter;
import com.team.blog.shared.security.MemberPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 탈퇴 유예 회원 차단 (004 FR-031, R-23, data-model §4-1).
 *
 * <p>로그인 세션의 {@code /api/**} 요청마다 {@link MemberQueryService#findAccessInfo(long)}(PK 1번)로 상태를 읽어
 * {@link MemberAccessInfo#REQUEST_ATTRIBUTE}에 두고(004 {@code CurrentViewerResolver}가 다시 조회하지 않게),
 * {@code WITHDRAWN}이면 허용 목록 밖 모든 요청(GET 포함)을 403 {@code ACCOUNT_WITHDRAWN} {@code details{action:
 * RESTORE}}로 막는다.
 *
 * <p>허용 목록: {@code POST /api/me/restore}(015), {@code POST /api/auth/logout}, {@code GET /api/me},
 * {@code GET /api/auth/csrf}. 쓰기 요청의 인증 전·정지 판정은 {@code AccountStatusGuard}(T037)가 그대로 맡는다.
 *
 * <p>Bean이 아니다 — 서블릿 필터로 따로 등록되지 않도록 {@link WithdrawnAccountGateCustomizer}가 만들어 보안 필터 체인의 {@code
 * AuthorizationFilter} 뒤에 붙인다.
 */
public class WithdrawnAccountGateFilter extends OncePerRequestFilter {

    private static final Set<String> ALLOWED =
            Set.of(
                    "POST /api/me/restore",
                    "POST /api/auth/logout",
                    "GET /api/me",
                    "GET /api/auth/csrf");

    private final MemberQueryService memberQueryService;
    private final ErrorResponseWriter errorWriter;

    public WithdrawnAccountGateFilter(
            MemberQueryService memberQueryService, ErrorResponseWriter errorWriter) {
        this.memberQueryService = memberQueryService;
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
        Optional<MemberPrincipal> principal = MemberPrincipal.current();
        if (principal.isPresent()) {
            Optional<MemberAccessInfo> info =
                    memberQueryService.findAccessInfo(principal.get().memberId());
            if (info.isPresent()) {
                request.setAttribute(MemberAccessInfo.REQUEST_ATTRIBUTE, info.get());
                if (info.get().status() == MemberStatus.WITHDRAWN && !isAllowed(request)) {
                    errorWriter.write(
                            response,
                            new AccountStateException(
                                    CommonReasonCode.ACCOUNT_WITHDRAWN,
                                    AccountStatusGuardService.RESTORE_DETAILS));
                    return;
                }
            }
        }
        chain.doFilter(request, response);
    }

    private static boolean isAllowed(HttpServletRequest request) {
        return ALLOWED.contains(request.getMethod() + " " + path(request));
    }

    private static String path(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        return context != null && !context.isEmpty() && uri.startsWith(context)
                ? uri.substring(context.length())
                : uri;
    }
}
