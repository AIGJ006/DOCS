package com.team.blog.account.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.team.blog.shared.web.SecurityHeadersFilter;
import com.team.blog.support.IntegrationTestBase;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 소셜 가입 마무리 화면만 소셜 사진 서버를 불러올 수 있다 (FR-032, R-21, 12 §8, H3). 다른 화면·API 응답의 {@code img-src}에는 두
 * 호스트가 없다.
 */
class SocialSignupPageCspIntegrationTest extends IntegrationTestBase {

    private static final String GOOGLE = "https://lh3.googleusercontent.com";
    private static final String GITHUB = "https://avatars.githubusercontent.com";

    @Test
    @DisplayName("/signup/social 응답의 img-src에만 Google·GitHub 사진 호스트가 있다")
    void onlySocialSignupPageAllowsSocialPhotoHosts() throws Exception {
        assertThat(imgSrc("/signup/social")).contains(GOOGLE, GITHUB);
        for (String path : new String[] {"/settings", "/", "/api/me", "/signup", "/login"}) {
            assertThat(imgSrc(path)).as(path).doesNotContain(GOOGLE, GITHUB);
        }
    }

    @Test
    @DisplayName("다른 정책 항목은 그대로다")
    void restOfPolicyUnchanged() throws Exception {
        String social = csp("/signup/social");
        String settings = csp("/settings");
        assertThat(social.replace(" " + GOOGLE, "").replace(" " + GITHUB, "")).isEqualTo(settings);
    }

    private String imgSrc(String path) throws Exception {
        return Arrays.stream(csp(path).split(";"))
                .map(String::strip)
                .filter(d -> d.startsWith("img-src"))
                .findFirst()
                .orElseThrow();
    }

    private String csp(String path) throws Exception {
        String header =
                mockMvc.perform(get(path))
                        .andReturn()
                        .getResponse()
                        .getHeader(SecurityHeadersFilter.CSP);
        assertThat(header).as(path).isNotNull();
        return header;
    }
}
