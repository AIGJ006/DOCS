package com.team.blog.post.integration;

import static com.team.blog.post.support.EditorApi.publishBody;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.team.blog.post.support.EditorApi;
import com.team.blog.shared.config.CoreProperties;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.MinioContainerSupport;
import com.team.blog.support.TestLogin;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MvcResult;

/** 에디터·미리보기·발행 응답의 보안 헤더 (002 T059, FR-049, QS §2). 필터 구현은 001. */
class SecurityHeadersIT extends IntegrationTestBase {

    @Autowired CoreProperties properties;

    private String expectedCsp() {
        URI uri = URI.create(properties.image().publicBaseUrl());
        String origin =
                uri.getScheme()
                        + "://"
                        + uri.getHost()
                        + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
        assertThat(properties.image().publicOrigin()).isEqualTo(origin);
        // 003 StorageCspContributor: 업로드 주소(테스트는 임의 포트 MinIO)의 출처가 공개 주소와 다르면 connect-src에 더한다
        return "default-src 'self'; script-src 'self'; connect-src 'self' "
                + origin
                + " "
                + MinioContainerSupport.endpoint()
                + "; img-src 'self' "
                + origin
                + " data: blob:; style-src 'self' 'unsafe-inline'; object-src 'none';"
                + " frame-ancestors 'none'; base-uri 'none'; form-action 'self'";
    }

    private void assertHeaders(MvcResult result, String what) {
        MockHttpServletResponse response = result.getResponse();
        assertThat(response.getHeader("Content-Security-Policy")).as(what).isEqualTo(expectedCsp());
        assertThat(response.getHeader("X-Content-Type-Options")).as(what).isEqualTo("nosniff");
        assertThat(response.getHeader("Referrer-Policy"))
                .as(what)
                .isEqualTo("strict-origin-when-cross-origin");
    }

    @Test
    void 미리보기_발행_에디터_화면_응답에_CSP와_보안_헤더() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        EditorApi api = new EditorApi(mockMvc);

        assertHeaders(api.preview(session, "본문"), "preview");
        long postId = api.createPostId(session);
        assertHeaders(
                api.publish(session, postId, publishBody("제목", "본문", List.of(), "PUBLIC", 0)),
                "publish");
        assertHeaders(mockMvc.perform(get("/")).andReturn(), "/");
        assertHeaders(mockMvc.perform(get("/write/1")).andReturn(), "/write/1");
        assertHeaders(mockMvc.perform(get("/write/new")).andReturn(), "/write/new");
    }
}
