package com.team.blog.shared.security;

import com.team.blog.shared.error.CommonReasonCode;
import com.team.blog.shared.error.ErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;

/**
 * 접근 거부 처리: CSRF 토큰 없음·불일치 → 403 {@code CSRF_REJECTED}. 그 밖에 로그인한 회원의 권한 부족은 볼 수 없는 것과 같게 404
 * {@code NOT_FOUND} (constitution III). 관리자 경로 등 다른 응답이 필요하면 기능이 경로 한정 처리기를 더한다(004).
 */
public class CsrfAccessDeniedHandler implements AccessDeniedHandler {

    private final ErrorResponseWriter writer;

    public CsrfAccessDeniedHandler(ErrorResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException)
            throws IOException {
        if (accessDeniedException instanceof CsrfException) {
            writer.write(response, CommonReasonCode.CSRF_REJECTED);
        } else {
            writer.write(response, CommonReasonCode.NOT_FOUND);
        }
    }
}
