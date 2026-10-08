package com.team.blog.account.infra.security;

import com.team.blog.account.domain.Provider;
import com.team.blog.account.infra.AccountProperties;
import com.team.blog.account.infra.AccountProperties.ClientCredentials;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Component;

/**
 * 소셜 로그인 등록 정보 (R-06, FR-001·030). Spring Boot의 {@code
 * spring.security.oauth2.client.registration.*}은 앱 키가 비어 있으면 시작이 실패하므로 쓰지 않고, {@code
 * blog.auth.social.google|github.client-id·client-secret}(환경 변수 {@code GOOGLE_CLIENT_*}·{@code
 * GITHUB_CLIENT_*})이 모두 있는 제공자만 등록한다. 키가 없는 제공자는 로그인 화면 버튼을 숨긴다({@code GET
 * /api/auth/social-providers}).
 *
 * <ul>
 *   <li>Google: OIDC, scope {@code openid,email,profile}.
 *   <li>GitHub: scope {@code read:user,user:email}.
 *   <li>redirect: {@code {baseUrl}/login/oauth2/code/{registrationId}}.
 * </ul>
 *
 * 네이버 등 다른 제공자는 넣지 않는다(FR-001).
 */
@Component
public class SocialClientRegistrations
        implements ClientRegistrationRepository, Iterable<ClientRegistration> {

    static final String REDIRECT_URI = "{baseUrl}/login/oauth2/code/{registrationId}";

    private final Map<String, ClientRegistration> registrations = new LinkedHashMap<>();
    private final List<Provider> enabled;

    public SocialClientRegistrations(AccountProperties properties) {
        AccountProperties.Social social = properties.auth().social();
        add(social.google(), CommonOAuth2Provider.GOOGLE, "google", "openid", "email", "profile");
        add(social.github(), CommonOAuth2Provider.GITHUB, "github", "read:user", "user:email");
        this.enabled =
                registrations.keySet().stream()
                        .map(id -> "google".equals(id) ? Provider.GOOGLE : Provider.GITHUB)
                        .toList();
    }

    private void add(
            ClientCredentials credentials,
            CommonOAuth2Provider provider,
            String registrationId,
            String... scopes) {
        if (credentials == null || !credentials.configured()) {
            return;
        }
        registrations.put(
                registrationId,
                provider.getBuilder(registrationId)
                        .clientId(credentials.clientId().strip())
                        .clientSecret(credentials.clientSecret().strip())
                        .scope(scopes)
                        .redirectUri(REDIRECT_URI)
                        .build());
    }

    /** 앱 키가 설정되어 로그인 버튼을 보일 제공자 (GOOGLE, GITHUB 순). */
    public List<Provider> enabledProviders() {
        return enabled;
    }

    @Override
    public ClientRegistration findByRegistrationId(String registrationId) {
        return registrationId == null ? null : registrations.get(registrationId);
    }

    @Override
    public Iterator<ClientRegistration> iterator() {
        return registrations.values().iterator();
    }
}
