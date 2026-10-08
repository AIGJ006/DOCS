package com.team.blog.account.infra.security;

import com.team.blog.account.application.LastActiveService;
import com.team.blog.account.infra.redis.ActiveTouchThrottle;
import com.team.blog.shared.security.MemberPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 최근 활동 갱신 (FR-059, R-26). 로그인한 {@code /api/**} 요청을 처리한 <b>뒤</b> {@link ActiveTouchThrottle}가
 * 허락하면(한 시간에 한 번) {@code member.last_active_at}을 지금으로 바꾼다. Redis·DB 오류는 경고 로그만 남기고 응답에 영향을 주지
 * 않는다(constitution V).
 *
 * <p>Bean이 아니다 — {@link AccountSecurityCustomizer}가 만들어 보안 필터 체인에 붙인다.
 */
public class LastActiveTouchFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LastActiveTouchFilter.class);

    private final ActiveTouchThrottle throttle;
    private final LastActiveService lastActiveService;

    public LastActiveTouchFilter(
            ActiveTouchThrottle throttle, LastActiveService lastActiveService) {
        this.throttle = throttle;
        this.lastActiveService = lastActiveService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        String path =
                context != null && !context.isEmpty() && uri.startsWith(context)
                        ? uri.substring(context.length())
                        : uri;
        return !(path.equals("/api") || path.startsWith("/api/"));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } finally {
            Optional<MemberPrincipal> principal = MemberPrincipal.current();
            if (principal.isPresent()) {
                touch(principal.get().memberId());
            }
        }
    }

    private void touch(long memberId) {
        try {
            if (throttle.tryAcquire(memberId)) {
                lastActiveService.touch(memberId);
            }
        } catch (RuntimeException e) {
            log.warn("최근 활동을 갱신하지 못했습니다: memberId={}", memberId, e);
        }
    }
}
