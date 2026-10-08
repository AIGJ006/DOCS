package com.team.blog.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 민감 값 로그 가림 (FR-015, R-11). 비밀번호·메일 링크 토큰은 어떤 로그에도 원문으로 남기지 않는다.
 *
 * <ul>
 *   <li>{@link #mask(String)}: {@code token}·{@code password}·{@code passwordConfirm}·{@code
 *       currentPassword}·{@code newPassword}·{@code newPasswordConfirm}과 012 검색어 {@code q}(FR-039)의
 *       쿼리·폼 값({@code name=값})과 JSON 값({@code "name":"값"})을 {@code ***}로 바꾼다. 애플리케이션 로그 문구는 {@code
 *       logback-spring.xml}의 {@code %maskedMsg}가 이 메서드를 거친다.
 *   <li>필터: 요청마다 가린 쿼리 문자열을 요청 속성 {@link #MASKED_QUERY_ATTRIBUTE}에 둔다. Tomcat 접근 로그 패턴({@code
 *       server.tomcat.accesslog.pattern})은 {@code %q} 대신 {@code %{blog.maskedQuery}r}을 쓴다.
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class SensitiveParamMasking extends OncePerRequestFilter {

    /** 가린 쿼리 문자열({@code ?…}, 없으면 빈 문자열)을 담는 요청 속성. */
    public static final String MASKED_QUERY_ATTRIBUTE = "blog.maskedQuery";

    private static final String NAMES =
            "token|password|passwordConfirm|currentPassword|newPassword|newPasswordConfirm|q";
    private static final Pattern PARAM =
            Pattern.compile("(?i)(?<![A-Za-z0-9_])(" + NAMES + ")=[^&\\s\"']*");
    private static final Pattern JSON_FIELD =
            Pattern.compile("(?i)\"(" + NAMES + ")\"\\s*:\\s*\"(?:[^\"\\\\]|\\\\.)*\"");

    /** 민감 값을 {@code ***}로 바꾼다. null은 null. */
    public static String mask(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String masked = PARAM.matcher(text).replaceAll("$1=***");
        return JSON_FIELD.matcher(masked).replaceAll("\"$1\":\"***\"");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String query = request.getQueryString();
        request.setAttribute(MASKED_QUERY_ATTRIBUTE, query == null ? "" : "?" + mask(query));
        chain.doFilter(request, response);
    }
}
