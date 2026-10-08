package com.team.blog.account.infra.security;

import java.util.List;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** 실제 GitHub API 호출. 사용자 정보 주소는 등록 정보({@code user-info-uri})를, 이메일은 {@code /user/emails}를 쓴다. */
@Component
public class RestGitHubUserClient implements GitHubUserClient {

    static final String EMAILS_URI = "https://api.github.com/user/emails";

    private final RestClient rest = RestClient.create();

    @Override
    public Map<String, Object> user(OAuth2UserRequest request) {
        String uri =
                request.getClientRegistration().getProviderDetails().getUserInfoEndpoint().getUri();
        try {
            Map<String, Object> body =
                    rest.get()
                            .uri(uri)
                            .header(HttpHeaders.AUTHORIZATION, bearer(request))
                            .accept(MediaType.APPLICATION_JSON)
                            .retrieve()
                            .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            if (body == null) {
                throw failure("empty user response");
            }
            return body;
        } catch (RestClientException e) {
            throw failure(e.getMessage());
        }
    }

    @Override
    public List<GitHubEmail> emails(OAuth2UserRequest request) {
        try {
            List<Map<String, Object>> body =
                    rest.get()
                            .uri(EMAILS_URI)
                            .header(HttpHeaders.AUTHORIZATION, bearer(request))
                            .accept(MediaType.APPLICATION_JSON)
                            .retrieve()
                            .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
            if (body == null) {
                return List.of();
            }
            return body.stream()
                    .map(
                            e ->
                                    new GitHubEmail(
                                            (String) e.get("email"),
                                            Boolean.TRUE.equals(e.get("primary")),
                                            Boolean.TRUE.equals(e.get("verified"))))
                    .toList();
        } catch (RestClientException e) {
            throw failure(e.getMessage());
        }
    }

    private static String bearer(OAuth2UserRequest request) {
        return "Bearer " + request.getAccessToken().getTokenValue();
    }

    private static OAuth2AuthenticationException failure(String description) {
        return new OAuth2AuthenticationException(
                new OAuth2Error("invalid_user_info_response", description, null));
    }
}
