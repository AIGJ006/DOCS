package com.team.blog.account.infra.security;

import com.team.blog.account.application.SocialLoginResult;
import com.team.blog.account.application.SocialLoginService;
import com.team.blog.shared.error.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
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
 * 소셜 콜백 성공 (openapi {@code socialLoginCallback}, R-07). 제공자 인증({@code OAuth2AuthenticationToken})은
 * 세션에 남기지 않고 바로 바꾼다.
 *
 * <ul>
 *   <li>연결된 계정: 회원 로그인({@link SessionLogin} — 세션 ID 재발급·{@code previousLoginAt}·{@code provider}) →
 *       보관한 {@code loginRedirect}로 302. 재동의가 필요하면 {@code /reagree}로 302(US5).
 *   <li>처음: 계정을 만들지 않고 세션 {@code pendingSocialSignup}만 둔 채 익명으로 전체 페이지 {@code /signup/social}로 302.
 *   <li>정지 계정 등 거부: 오류 본문을 세션에 한 번 보관하고 {@code /login?error=social}로 302.
 * </ul>
 */
@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    public static final String SIGNUP_PAGE = "/signup/social";
    public static final String ERROR_PAGE = "/login?error=social";
    public static final String REAGREE_PAGE = "/reagree";

    private final SocialLoginService socialLoginService;
    private final SessionLogin sessionLogin;
    private final JsonMapper jsonMapper;
    private final SecurityContextHolderStrategy contextHolder =
            SecurityContextHolder.getContextHolderStrategy();
    private final SecurityContextRepository contextRepository =
            new HttpSessionSecurityContextRepository();

    public OAuth2LoginSuccessHandler(
            SocialLoginService socialLoginService,
            SessionLogin sessionLogin,
            JsonMapper jsonMapper) {
        this.socialLoginService = socialLoginService;
        this.sessionLogin = sessionLogin;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {
        SocialUser user = (SocialUser) authentication.getPrincipal();
        clearAuthentication(request, response);
        HttpSession session = request.getSession(true);

        SocialLoginResult result;
        try {
            result = socialLoginService.login(user.profile());
        } catch (ApiException e) {
            session.removeAttribute(SocialLoginSession.PENDING_SIGNUP);
            session.removeAttribute(SocialLoginSession.LOGIN_REDIRECT);
            session.setAttribute(
                    SocialLoginSession.LOGIN_ERROR, jsonMapper.writeValueAsString(e.toResponse()));
            response.sendRedirect(ERROR_PAGE);
            return;
        }

        switch (result) {
            case SocialLoginResult.LoggedIn loggedIn -> {
                session.removeAttribute(SocialLoginSession.PENDING_SIGNUP);
                String target = SocialLoginSession.takeRedirect(session);
                sessionLogin.login(
                        loggedIn.memberId(),
                        loggedIn.role().name(),
                        user.profile().provider(),
                        loggedIn.outcome(),
                        request,
                        response);
                response.sendRedirect(
                        loggedIn.outcome().reagreementRequired() ? REAGREE_PAGE : target);
            }
            case SocialLoginResult.PendingSignup pending -> {
                session.setAttribute(SocialLoginSession.PENDING_SIGNUP, pending.pending());
                response.sendRedirect(SIGNUP_PAGE);
            }
        }
    }

    private void clearAuthentication(HttpServletRequest request, HttpServletResponse response) {
        SecurityContext empty = contextHolder.createEmptyContext();
        contextHolder.setContext(empty);
        contextRepository.saveContext(empty, request, response);
    }
}
