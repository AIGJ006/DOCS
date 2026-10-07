package com.team.blog.shared.security;

import com.team.blog.shared.error.CommonReasonCode;
import com.team.blog.shared.error.ErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/** 비로그인 요청 → 401 {@code LOGIN_REQUIRED} JSON (리다이렉트 없음). React가 로그인 화면으로 안내한다. */
public class LoginRequiredEntryPoint implements AuthenticationEntryPoint {

    private final ErrorResponseWriter writer;

    public LoginRequiredEntryPoint(ErrorResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException)
            throws IOException {
        writer.write(response, CommonReasonCode.LOGIN_REQUIRED);
    }
}
