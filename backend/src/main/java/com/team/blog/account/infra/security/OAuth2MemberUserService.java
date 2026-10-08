package com.team.blog.account.infra.security;

import com.team.blog.account.application.EmailAddress;
import com.team.blog.account.application.SocialProfile;
import com.team.blog.account.domain.Provider;
import java.util.Map;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

/**
 * GitHub 사용자 정보 (R-06, FR-003·008). 숫자 {@code id}로 식별하고({@code login}은 바뀔 수 있어 식별에 쓰지 않는다), {@code
 * /user/emails}에서 {@code primary && verified}인 이메일을 받는다. 없으면 이메일 없이 넘기고 마무리 화면이 입력받는다.
 */
@Component
public class OAuth2MemberUserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final GitHubUserClient github;

    public OAuth2MemberUserService(GitHubUserClient github) {
        this.github = github;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        String registrationId = request.getClientRegistration().getRegistrationId();
        if (!"github".equals(registrationId)) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("unsupported_provider", registrationId, null));
        }
        Map<String, Object> user = github.user(request);
        Object id = user.get("id");
        if (!(id instanceof Number number)) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("invalid_user_info_response", "id 없음", null));
        }
        String email =
                github.emails(request).stream()
                        .filter(e -> e.primary() && e.verified() && e.email() != null)
                        .map(e -> EmailAddress.normalize(e.email()))
                        .findFirst()
                        .orElse(null);
        String name = text(user.get("name"));
        SocialProfile profile =
                new SocialProfile(
                        Provider.GITHUB,
                        Long.toString(number.longValue()),
                        email,
                        email != null,
                        name != null ? name : text(user.get("login")),
                        text(user.get("avatar_url")));
        return new SocialUser(profile, null);
    }

    private static String text(Object value) {
        return value instanceof String s && !s.isBlank() ? s : null;
    }
}
