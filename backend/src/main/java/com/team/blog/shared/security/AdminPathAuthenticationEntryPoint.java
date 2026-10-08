package com.team.blog.shared.security;

import com.team.blog.shared.error.ErrorResponseWriter;
import com.team.blog.shared.web.CacheControlPolicy;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * 관리자 경로의 비회원 진입점 (004 T065, research R-22). 있는 주소·없는 주소가 같은 응답이다.
 *
 * <ul>
 *   <li>{@code /api/admin/**}: 001 {@link LoginRequiredEntryPoint}에 맡긴다(401 {@code LOGIN_REQUIRED}
 *       JSON).
 *   <li>{@code /admin/**} 화면: 401 상태로 React 셸({@code classpath:static/index.html}, 링크 미리보기 메타 없음)을
 *       내려 화면이 {@code /login?returnTo=}로 안내한다({@code AdminRouteGate}). {@code Cache-Control:
 *       private, no-store}.
 * </ul>
 */
public class AdminPathAuthenticationEntryPoint implements AuthenticationEntryPoint {

    static final String FALLBACK_SHELL =
            "<!doctype html><html lang=\"ko\"><head><meta charset=\"UTF-8\">"
                    + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">"
                    + "<title>블로그</title></head><body><div id=\"root\"></div></body></html>";

    private final AuthenticationEntryPoint api;
    private final byte[] shell;

    public AdminPathAuthenticationEntryPoint(ErrorResponseWriter writer, Resource shell) {
        this.api = new LoginRequiredEntryPoint(writer);
        this.shell = readShell(shell).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException)
            throws IOException, ServletException {
        if (!AdminPaths.PAGES.matches(request)) {
            api.commence(request, response, authException);
            return;
        }
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setHeader(HttpHeaders.CACHE_CONTROL, CacheControlPolicy.NO_STORE);
        response.setContentType("text/html;charset=UTF-8");
        response.setContentLength(shell.length);
        response.getOutputStream().write(shell);
        response.flushBuffer();
    }

    private static String readShell(Resource shell) {
        if (shell == null || !shell.exists()) {
            return FALLBACK_SHELL;
        }
        try (InputStream in = shell.getInputStream()) {
            // 005 셸 규약의 메타 자리는 비워 둔다 (관리자 화면 메타를 내리지 않는다)
            return new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("<!--app-head-->", "");
        } catch (IOException e) {
            return FALLBACK_SHELL;
        }
    }
}
