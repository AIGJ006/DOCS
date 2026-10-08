package com.team.blog.account.infra.security;

import com.team.blog.account.application.LoginOutcome;
import com.team.blog.account.application.LoginService;
import com.team.blog.account.domain.Provider;
import com.team.blog.account.infra.redis.LoginFailureCounter;
import com.team.blog.account.web.dto.LoginResult;
import com.team.blog.shared.error.ApiException;
import com.team.blog.shared.error.ErrorResponseWriter;
import com.team.blog.shared.security.MemberPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 이메일 로그인 성공 → 200 {@link LoginResult} JSON (리다이렉트 없음, React가 이동을 정한다 — R-04). 세션 ID는 Spring
 * Security가 이미 새로 발급했다(세션 고정 방지). {@link LoginService#onSuccess}로 정지 판정·{@code last_login_at} 갱신을
 * 하고 갱신 전 값을 세션에 담는다.
 *
 * <ul>
 *   <li>이동 주소: 폼의 {@code redirect}를 {@link SafeRedirectResolver}로 검사한 값, 아니면 {@code /} (FR-039).
 *   <li>정지 계정: 인증을 되돌리고 403 {@code ACCOUNT_SUSPENDED} {@code details {endsAt, reason}} (FR-038,
 *       R-23).
 *   <li>성공하면 그 이메일의 로그인 실패 횟수를 지운다(FR-035). 카운터는 Redis 쓰기라 트랜잭션 밖인 여기서 부른다.
 *   <li>재동의가 필요하면 세션 {@code reagreementRequired}를 두고({@link LoginSession}) {@code
 *       reagreementRequired:true}로 알린다(FR-012).
 * </ul>
 */
@Component
public class JsonLoginSuccessHandler implements AuthenticationSuccessHandler {

    public static final String REDIRECT_PARAMETER = "redirect";

    private final LoginService loginService;
    private final LoginFailureCounter failureCounter;
    private final SafeRedirectResolver safeRedirect;
    private final ErrorResponseWriter errorWriter;
    private final JsonMapper jsonMapper;
    private final SecurityContextHolderStrategy contextHolder =
            SecurityContextHolder.getContextHolderStrategy();
    private final SecurityContextRepository contextRepository =
            new HttpSessionSecurityContextRepository();

    public JsonLoginSuccessHandler(
            LoginService loginService,
            LoginFailureCounter failureCounter,
            SafeRedirectResolver safeRedirect,
            ErrorResponseWriter errorWriter,
            JsonMapper jsonMapper) {
        this.loginService = loginService;
        this.failureCounter = failureCounter;
        this.safeRedirect = safeRedirect;
        this.errorWriter = errorWriter;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {
        MemberPrincipal principal = (MemberPrincipal) authentication.getPrincipal();
        LoginOutcome outcome;
        try {
            outcome = loginService.onSuccess(principal.memberId());
        } catch (ApiException e) {
            SecurityContext empty = contextHolder.createEmptyContext();
            contextHolder.setContext(empty);
            contextRepository.saveContext(empty, request, response);
            errorWriter.write(response, e);
            return;
        }
        failureCounter.reset(request.getParameter("email"));
        LoginSession.record(request.getSession(), Provider.LOCAL, outcome);
        LoginResult body =
                new LoginResult(
                        safeRedirect.resolve(request.getParameter(REDIRECT_PARAMETER)),
                        outcome.reagreementRequired(),
                        outcome.status().name());
        response.setStatus(HttpServletResponse.SC_OK);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(jsonMapper.writeValueAsString(body));
    }
}
