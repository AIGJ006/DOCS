package com.team.blog.account.infra.security;

import com.team.blog.account.application.SocialProfile;
import java.io.Serial;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/**
 * 소셜 사용자 정보 서비스가 돌려주는 사용자. 콜백 처리 동안에만 쓰이고, {@link OAuth2LoginSuccessHandler}가 곧바로 회원 로그인({@code
 * MemberPrincipal}) 또는 익명(가입 대기)으로 바꾼다. 권한은 없다.
 */
public final class SocialUser implements OidcUser {

    @Serial private static final long serialVersionUID = 1L;

    private final SocialProfile profile;
    private final OidcIdToken idToken;

    SocialUser(SocialProfile profile, OidcIdToken idToken) {
        this.profile = profile;
        this.idToken = idToken;
    }

    public SocialProfile profile() {
        return profile;
    }

    @Override
    public Map<String, Object> getClaims() {
        return idToken == null ? Map.of() : idToken.getClaims();
    }

    @Override
    public OidcUserInfo getUserInfo() {
        return null;
    }

    @Override
    public OidcIdToken getIdToken() {
        return idToken;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return Map.of("provider", profile.provider().name(), "id", profile.providerUserId());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public String getName() {
        return profile.provider().name() + ":" + profile.providerUserId();
    }
}
