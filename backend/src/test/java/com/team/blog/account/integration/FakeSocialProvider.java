package com.team.blog.account.integration;

import com.team.blog.account.infra.security.GitHubUserClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.stereotype.Component;

/**
 * test 프로필 전용 가짜 Google·GitHub (R-35). 실제 제공자를 부르지 않고 Spring Security OAuth2 Client의 콜백 흐름 전체(state
 * 확인 → 토큰 교환 → ID 토큰 검증 → 사용자 정보)를 그대로 돌린다.
 *
 * <ul>
 *   <li>토큰 교환({@link OAuth2AccessTokenResponseClient} Bean — OAuth2LoginConfigurer가 찾아 쓴다): 콜백의
 *       {@code code}를 그대로 액세스 토큰으로 돌려주고, Google이면 등록해 둔 클레임으로 {@code id_token}을 만든다(요청의 nonce 해시
 *       포함).
 *   <li>ID 토큰 해석({@link JwtDecoderFactory} Bean): 서명 검사 없이 등록한 클레임을 돌려준다.
 *   <li>GitHub {@code /user}·{@code /user/emails}({@link GitHubUserClient}, 운영 Bean보다 우선): 토큰(=
 *       code)으로 등록한 값을 돌려준다.
 * </ul>
 *
 * 테스트는 {@link #google}·{@link #github}로 code별 사용자를 등록한 뒤 콜백을 그 code로 부른다.
 */
@Profile("test")
@Primary
@Component
public class FakeSocialProvider
        implements OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest>,
                JwtDecoderFactory<ClientRegistration>,
                GitHubUserClient {

    private final Map<String, Map<String, Object>> googleClaims = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> idTokens = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> githubUsers = new ConcurrentHashMap<>();
    private final Map<String, List<GitHubEmail>> githubEmails = new ConcurrentHashMap<>();

    /** Google 사용자 등록. {@code picture}는 null 가능. */
    public void google(
            String code,
            String sub,
            String email,
            boolean emailVerified,
            String name,
            String picture) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", sub);
        claims.put("email", email);
        claims.put("email_verified", emailVerified);
        if (name != null) {
            claims.put("name", name);
        }
        if (picture != null) {
            claims.put("picture", picture);
        }
        googleClaims.put(code, claims);
    }

    /** GitHub 사용자 등록. {@code id}는 숫자 ID. */
    public void github(
            String code,
            long id,
            String login,
            String name,
            String avatarUrl,
            List<GitHubEmail> emails) {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", id);
        user.put("login", login);
        if (name != null) {
            user.put("name", name);
        }
        if (avatarUrl != null) {
            user.put("avatar_url", avatarUrl);
        }
        githubUsers.put(code, user);
        githubEmails.put(code, List.copyOf(emails));
    }

    @Override
    public OAuth2AccessTokenResponse getTokenResponse(
            OAuth2AuthorizationCodeGrantRequest grantRequest) {
        String code = grantRequest.getAuthorizationExchange().getAuthorizationResponse().getCode();
        String registrationId = grantRequest.getClientRegistration().getRegistrationId();
        Map<String, Object> additional = new HashMap<>();
        if ("google".equals(registrationId)) {
            Map<String, Object> claims = googleClaims.get(code);
            if (claims == null) {
                throw new OAuth2AuthenticationException(new OAuth2Error("invalid_grant"));
            }
            Map<String, Object> idToken = new HashMap<>(claims);
            idToken.put("iss", "https://accounts.google.com");
            idToken.put("aud", List.of(grantRequest.getClientRegistration().getClientId()));
            Object nonce =
                    grantRequest
                            .getAuthorizationExchange()
                            .getAuthorizationRequest()
                            .getAttribute("nonce");
            if (nonce != null) {
                idToken.put("nonce", sha256(nonce.toString()));
            }
            String idTokenValue = "idt-" + code;
            idTokens.put(idTokenValue, idToken);
            additional.put("id_token", idTokenValue);
        } else if (!githubUsers.containsKey(code)) {
            throw new OAuth2AuthenticationException(new OAuth2Error("invalid_grant"));
        }
        return OAuth2AccessTokenResponse.withToken(code)
                .tokenType(OAuth2AccessToken.TokenType.BEARER)
                .expiresIn(3600)
                .scopes(grantRequest.getClientRegistration().getScopes())
                .additionalParameters(additional)
                .build();
    }

    @Override
    public JwtDecoder createDecoder(ClientRegistration context) {
        return token -> {
            Map<String, Object> claims = idTokens.get(token);
            if (claims == null) {
                throw new org.springframework.security.oauth2.jwt.BadJwtException("unknown token");
            }
            Instant now = Instant.now();
            return Jwt.withTokenValue(token)
                    .header("alg", "RS256")
                    .claims(c -> c.putAll(claims))
                    .issuedAt(now)
                    .expiresAt(now.plusSeconds(3600))
                    .build();
        };
    }

    @Override
    public Map<String, Object> user(OAuth2UserRequest request) {
        Map<String, Object> user = githubUsers.get(request.getAccessToken().getTokenValue());
        if (user == null) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(OAuth2ParameterNames.ERROR, "unknown", null));
        }
        return user;
    }

    @Override
    public List<GitHubEmail> emails(OAuth2UserRequest request) {
        return githubEmails.getOrDefault(request.getAccessToken().getTokenValue(), List.of());
    }

    private static String sha256(String value) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
