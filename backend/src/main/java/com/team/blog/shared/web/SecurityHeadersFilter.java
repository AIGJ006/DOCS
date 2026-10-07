package com.team.blog.shared.web;

import com.team.blog.shared.config.CoreProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 모든 응답(API·HTML·정적 파일)에 보안 헤더를 넣는다 (12 §8, 02 §5).
 *
 * <ul>
 *   <li>{@code Content-Security-Policy}: {@code default-src 'self'; script-src 'self'; connect-src
 *       'self' {저장소}; img-src 'self' {저장소} data: blob:; style-src 'self' 'unsafe-inline';
 *       object-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'}. {저장소} =
 *       {@code blog.image.public-base-url}의 출처.
 *   <li>{@code X-Content-Type-Options: nosniff}, {@code Referrer-Policy:
 *       strict-origin-when-cross-origin}.
 * </ul>
 *
 * {@link CspContributor} Bean이 맞는 요청에만 {@code img-src}를 더한다. 002 plan의 {@code
 * SecurityHeadersConfig}는 이 필터를 가리킨다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityHeadersFilter extends OncePerRequestFilter {

    public static final String CSP = "Content-Security-Policy";

    private final String storageOrigin;
    private final List<CspContributor> contributors;
    private final String defaultPolicy;

    public SecurityHeadersFilter(CoreProperties properties, List<CspContributor> contributors) {
        this.storageOrigin = properties.image().publicOrigin();
        this.contributors = List.copyOf(contributors);
        this.defaultPolicy = policy(List.of());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        response.setHeader(CSP, policyFor(request));
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    private String policyFor(HttpServletRequest request) {
        if (contributors.isEmpty()) {
            return defaultPolicy;
        }
        List<String> extra = new ArrayList<>();
        for (CspContributor contributor : contributors) {
            if (contributor.appliesTo(request)) {
                extra.addAll(contributor.extraImgSrc());
            }
        }
        return extra.isEmpty() ? defaultPolicy : policy(extra);
    }

    private String policy(List<String> extraImgSrc) {
        Set<String> img = new LinkedHashSet<>(List.of("'self'", storageOrigin, "data:", "blob:"));
        img.addAll(extraImgSrc);
        return String.join(
                "; ",
                "default-src 'self'",
                "script-src 'self'",
                "connect-src 'self' " + storageOrigin,
                "img-src " + String.join(" ", img),
                "style-src 'self' 'unsafe-inline'",
                "object-src 'none'",
                "frame-ancestors 'none'",
                "base-uri 'none'",
                "form-action 'self'");
    }
}
