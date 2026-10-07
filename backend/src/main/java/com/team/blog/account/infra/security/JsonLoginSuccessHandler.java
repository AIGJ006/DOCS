package com.team.blog.account.infra.security;

import com.team.blog.account.application.LoginOutcome;
import com.team.blog.account.application.LoginService;
import com.team.blog.account.domain.Provider;
import com.team.blog.account.web.dto.LoginResult;
import com.team.blog.shared.security.MemberPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 이메일 로그인 성공 → 200 {@link LoginResult} JSON (리다이렉트 없음, React가 이동을 정한다 — R-04). 세션 ID는 Spring
 * Security가 이미 새로 발급했다(세션 고정 방지). {@link LoginService#onSuccess}로 {@code last_login_at}을 갱신하고 갱신 전
 * 값을 세션에 담는다.
 *
 * <p>이동 주소는 지금은 항상 {@code /}이다. 요청의 {@code redirect} 검사(SafeRedirectResolver)는 US5에서 연결한다.
 */
@Component
public class JsonLoginSuccessHandler implements AuthenticationSuccessHandler {

    private final LoginService loginService;
    private final JsonMapper jsonMapper;

    public JsonLoginSuccessHandler(LoginService loginService, JsonMapper jsonMapper) {
        this.loginService = loginService;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {
        MemberPrincipal principal = (MemberPrincipal) authentication.getPrincipal();
        LoginOutcome outcome = loginService.onSuccess(principal.memberId());
        LoginSession.record(request.getSession(), Provider.LOCAL, outcome);
        LoginResult body =
                new LoginResult("/", outcome.reagreementRequired(), outcome.status().name());
        response.setStatus(HttpServletResponse.SC_OK);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(jsonMapper.writeValueAsString(body));
    }
}
