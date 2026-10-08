package com.team.blog.account.web;

import com.team.blog.account.infra.AccountProperties;
import com.team.blog.shared.web.CspContributor;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * 소셜 가입 마무리 화면({@code GET /signup/social} HTML 응답)에만 소셜 사진 호스트({@code
 * blog.auth.social.photo-hosts})를 {@code https://} 출처로 {@code img-src}에 더한다 (FR-032, R-21, H3).
 * plan의 {@code SocialSignupPageCspFilter}에 해당한다.
 */
@Component
public class SocialSignupPageCspContributor implements CspContributor {

    static final String PAGE = "/signup/social";

    private final List<String> origins;

    public SocialSignupPageCspContributor(AccountProperties properties) {
        this.origins =
                properties.auth().social().photoHosts().stream()
                        .map(h -> "https://" + h.strip().toLowerCase(Locale.ROOT))
                        .toList();
    }

    @Override
    public boolean appliesTo(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return ("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod()))
                && (PAGE.equals(path) || (PAGE + "/").equals(path));
    }

    @Override
    public List<String> extraImgSrc() {
        return origins;
    }
}
