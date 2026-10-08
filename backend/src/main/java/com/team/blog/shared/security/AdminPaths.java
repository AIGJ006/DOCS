package com.team.blog.shared.security;

import com.team.blog.account.application.MemberAccessInfo;
import com.team.blog.account.application.MemberQueryService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;

/**
 * 관리자 경로와 관리자 여부 (004 US7, FR-044, research R-13·R-22). 화면 {@code /admin/**}과 API {@code
 * /api/admin/**}.
 *
 * <p>관리자 여부는 세션에 든 역할이 아니라 매 요청 DB 역할(001 {@link MemberQueryService#findAccessInfo}, PK 1번)로 본다 —
 * 관리자에서 내려간 회원의 남은 세션이 곧바로 일반 회원으로 처리된다.
 */
@Component
public class AdminPaths {

    /** 관리자 화면 경로 */
    public static final RequestMatcher PAGES =
            PathPatternRequestMatcher.withDefaults().matcher("/admin/**");

    /** 관리자 API 경로 */
    public static final RequestMatcher API =
            PathPatternRequestMatcher.withDefaults().matcher("/api/admin/**");

    /** 둘 다 */
    public static final RequestMatcher ALL = new OrRequestMatcher(PAGES, API);

    private final ObjectProvider<MemberQueryService> memberQueryService;

    public AdminPaths(ObjectProvider<MemberQueryService> memberQueryService) {
        this.memberQueryService = memberQueryService;
    }

    /** 로그인한 회원인가 (익명 토큰 제외). */
    public static boolean isMember(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)
                && authentication.getPrincipal() instanceof MemberPrincipal;
    }

    /** 지금 DB 역할이 관리자인가. 001 탈퇴 게이트가 같은 요청에서 읽어 둔 값이 있으면 다시 조회하지 않는다. */
    public boolean isAdmin(Authentication authentication, HttpServletRequest request) {
        if (!isMember(authentication)) {
            return false;
        }
        Object cached =
                request == null ? null : request.getAttribute(MemberAccessInfo.REQUEST_ATTRIBUTE);
        long memberId = ((MemberPrincipal) authentication.getPrincipal()).memberId();
        if (cached instanceof MemberAccessInfo info) {
            return info.isAdmin();
        }
        return memberQueryService
                .getObject()
                .findAccessInfo(memberId)
                .map(MemberAccessInfo::isAdmin)
                .orElse(false);
    }
}
