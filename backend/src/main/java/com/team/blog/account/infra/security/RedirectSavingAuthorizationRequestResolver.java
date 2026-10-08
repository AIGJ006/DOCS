package com.team.blog.account.infra.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/**
 * 소셜 로그인 시작({@code GET /oauth2/authorization/{google|github}?redirect=…}): 기본 요청(state·nonce)을 만들고,
 * {@code redirect}를 {@link SafeRedirectResolver}로 검사해 세션 {@code loginRedirect}에 보관한다(R-33). 남아 있던
 * 콜백 오류는 지운다.
 */
class RedirectSavingAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    private final OAuth2AuthorizationRequestResolver delegate;
    private final SafeRedirectResolver safeRedirect;

    RedirectSavingAuthorizationRequestResolver(
            ClientRegistrationRepository registrations, SafeRedirectResolver safeRedirect) {
        this.delegate =
                new DefaultOAuth2AuthorizationRequestResolver(
                        registrations,
                        OAuth2AuthorizationRequestRedirectFilter
                                .DEFAULT_AUTHORIZATION_REQUEST_BASE_URI);
        this.safeRedirect = safeRedirect;
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return remember(request, delegate.resolve(request));
    }

    @Override
    public OAuth2AuthorizationRequest resolve(
            HttpServletRequest request, String clientRegistrationId) {
        return remember(request, delegate.resolve(request, clientRegistrationId));
    }

    private OAuth2AuthorizationRequest remember(
            HttpServletRequest request, OAuth2AuthorizationRequest authorizationRequest) {
        if (authorizationRequest != null) {
            var session = request.getSession(true);
            session.setAttribute(
                    SocialLoginSession.LOGIN_REDIRECT,
                    safeRedirect.resolve(request.getParameter("redirect")));
            session.removeAttribute(SocialLoginSession.LOGIN_ERROR);
        }
        return authorizationRequest;
    }
}
