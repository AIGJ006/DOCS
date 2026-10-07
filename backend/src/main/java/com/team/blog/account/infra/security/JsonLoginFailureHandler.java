package com.team.blog.account.infra.security;

import com.team.blog.account.application.AccountReasonCode;
import com.team.blog.shared.error.ErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

/**
 * 이메일 로그인 실패 → 항상 401 {@code INVALID_CREDENTIALS} "이메일 또는 비밀번호가 올바르지 않아요" (FR-036). 어느 칸이 틀렸는지·가입
 * 여부를 알려주지 않는다. 잠금(429)·정지(403)는 US5에서 이 처리기 앞뒤에 더한다.
 */
@Component
public class JsonLoginFailureHandler implements AuthenticationFailureHandler {

    private final ErrorResponseWriter writer;

    public JsonLoginFailureHandler(ErrorResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
            throws IOException {
        writer.write(response, AccountReasonCode.INVALID_CREDENTIALS);
    }
}
