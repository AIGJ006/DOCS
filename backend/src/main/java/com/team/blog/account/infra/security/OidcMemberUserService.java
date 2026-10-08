package com.team.blog.account.infra.security;

import com.team.blog.account.application.EmailAddress;
import com.team.blog.account.application.SocialProfile;
import com.team.blog.account.domain.Provider;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

/**
 * Google(OIDC) 사용자 정보 (R-06, FR-003·008). ID 토큰의 {@code sub}로 식별하고 {@code email_verified = true}인
 * 이메일만 받는다 — 확인되지 않은 이메일이면 로그인 자체를 거부한다({@code SOCIAL_EMAIL_NOT_VERIFIED}). 이름·사진도 ID 토큰 클레임에서 읽으므로
 * UserInfo 엔드포인트는 부르지 않는다.
 */
@Component
public class OidcMemberUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    static final String EMAIL_NOT_VERIFIED = "email_not_verified";

    private final OidcUserService delegate = new OidcUserService();

    public OidcMemberUserService() {
        delegate.setRetrieveUserInfo(request -> false);
    }

    @Override
    public OidcUser loadUser(OidcUserRequest request) throws OAuth2AuthenticationException {
        OidcUser user = delegate.loadUser(request);
        String email = user.getEmail();
        if (!Boolean.TRUE.equals(user.getEmailVerified()) || email == null || email.isBlank()) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(EMAIL_NOT_VERIFIED, "Google 이메일이 확인되지 않았습니다", null));
        }
        SocialProfile profile =
                new SocialProfile(
                        Provider.GOOGLE,
                        user.getSubject(),
                        EmailAddress.normalize(email),
                        true,
                        user.getFullName(),
                        user.getPicture());
        return new SocialUser(profile, user.getIdToken());
    }
}
