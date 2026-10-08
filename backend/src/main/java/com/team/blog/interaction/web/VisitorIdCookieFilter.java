package com.team.blog.interaction.web;

import com.team.blog.interaction.application.VisitorKeyResolver;
import com.team.blog.shared.security.MemberPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 비회원 방문자 쿠키 {@code vid} 발급 (009 T033, contracts/view-pipeline.md §2, research R5).
 *
 * <ul>
 *   <li>대상: {@code GET /api/posts/{postId}}(005 상세 API)와 {@code GET /@{handle}/posts/{postId}}(005
 *       페이지 셸)
 *   <li>조건: 로그인하지 않았고 형식이 맞는 {@code vid}가 없음
 *   <li>동작: {@code Set-Cookie: vid={UUID}; Max-Age=1년; Path=/; HttpOnly; SameSite=Lax[; Secure]}.
 *       응답 상태와 상관없이(404여도) 붙인다 — 붙이는 조건으로 글 존재가 드러나지 않게
 * </ul>
 *
 * 조회 기록 요청에서 처음 쿠키를 주면 첫 조회(해시 키)와 새로고침(쿠키 키)이 다른 방문자로 세어지므로, 상세를 열 때 미리 준다. {@code Secure}는 001 세션
 * 쿠키 설정({@code server.servlet.session.cookie.secure})을 따른다. 005 코드는 바꾸지 않고 응답 헤더만 더한다. 보안 필터 체인 뒤에
 * 등록해 로그인 여부를 안다({@link ViewWebConfig}).
 */
public class VisitorIdCookieFilter extends OncePerRequestFilter {

    public static final String COOKIE_NAME = "vid";

    private static final Pattern DETAIL_API = Pattern.compile("^/api/posts/[^/]+/?$");
    private static final Pattern DETAIL_PAGE = Pattern.compile("^/@[^/]+/posts/[^/]+/?$");

    private final Duration maxAge;
    private final boolean secure;

    public VisitorIdCookieFilter(Duration maxAge, boolean secure) {
        this.maxAge = maxAge;
        this.secure = secure;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !DETAIL_API.matcher(path).matches() && !DETAIL_PAGE.matcher(path).matches();
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (MemberPrincipal.current().isEmpty() && !hasValidVid(request)) {
            ResponseCookie cookie =
                    ResponseCookie.from(COOKIE_NAME, UUID.randomUUID().toString())
                            .maxAge(maxAge)
                            .path("/")
                            .httpOnly(true)
                            .secure(secure)
                            .sameSite("Lax")
                            .build();
            response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        }
        chain.doFilter(request, response);
    }

    private static boolean hasValidVid(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return false;
        }
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName())
                    && VisitorKeyResolver.isValidVid(cookie.getValue())) {
                return true;
            }
        }
        return false;
    }
}
