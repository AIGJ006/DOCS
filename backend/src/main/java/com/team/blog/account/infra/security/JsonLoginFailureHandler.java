package com.team.blog.account.infra.security;

import com.team.blog.account.application.AccountReasonCode;
import com.team.blog.account.infra.redis.LoginFailureCounter;
import com.team.blog.shared.error.ErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

/**
 * 이메일 로그인 실패 → 항상 401 {@code INVALID_CREDENTIALS} "이메일 또는 비밀번호가 올바르지 않아요" (FR-036). 어느 칸이 틀렸는지·가입
 * 여부를 알려주지 않는다. 실패마다 {@link LoginFailureCounter}로 그 이메일의 실패 횟수를 센다(가입 여부와 무관, FR-035). 잠긴 뒤의
 * 거부(429)는 {@link LoginRateLimitFilter}가, 정지(403)는 {@link JsonLoginSuccessHandler}가 맡는다.
 */
@Component
public class JsonLoginFailureHandler implements AuthenticationFailureHandler {

    private final ErrorResponseWriter writer;
    private final LoginFailureCounter failureCounter;

    public JsonLoginFailureHandler(ErrorResponseWriter writer, LoginFailureCounter failureCounter) {
        this.writer = writer;
        this.failureCounter = failureCounter;
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
            throws IOException {
        failureCounter.recordFailure(request.getParameter("email"));
        writer.write(response, AccountReasonCode.INVALID_CREDENTIALS);
    }
}
