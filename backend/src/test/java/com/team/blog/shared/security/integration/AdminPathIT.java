package com.team.blog.shared.security.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.team.blog.shared.web.NotFoundPageRenderer;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 관리자 화면 주소 숨기기 (004 T063, US7, FR-044, SC-007, research R-13·R-22). {@code /admin/**}·{@code
 * /api/admin/**}는 비회원에게 있는 주소·없는 주소 모두 같은 401, 로그인한 일반 회원에게 공통 404다 — 관리자 기능(014)의 존재가 드러나지 않는다. 있는
 * 경로는 테스트 전용 {@code GET /api/admin/__probe}.
 */
class AdminPathIT extends IntegrationTestBase {

    private static final String LOGIN_REQUIRED =
            "{\"code\":\"LOGIN_REQUIRED\",\"message\":\"로그인이 필요해요\",\"errors\":[],\"details\":null}";
    private static final String NOT_FOUND =
            "{\"code\":\"NOT_FOUND\",\"message\":\"볼 수 없는 페이지예요\",\"errors\":[],\"details\":null}";

    @Autowired private NotFoundPageRenderer notFoundPageRenderer;

    /** 비교할 응답 부분 ({@code Date} 등 매번 다른 헤더 제외). */
    private record Shape(int status, String contentType, String cacheControl, String body) {}

    private Shape send(MockHttpServletRequestBuilder request, Cookie session) throws Exception {
        if (session != null) {
            request.cookie(session);
        }
        MockHttpServletResponse r = mockMvc.perform(request).andReturn().getResponse();
        return new Shape(
                r.getStatus(),
                r.getContentType(),
                r.getHeader("Cache-Control"),
                r.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    private List<MockHttpServletRequestBuilder> pageRequests(Cookie session) {
        return List.of(
                get("/admin/reports"),
                get("/admin/xyz"),
                get("/admin"),
                TestLogin.withCsrf(post("/admin/reports"), session),
                post("/admin/xyz"));
    }

    private List<MockHttpServletRequestBuilder> apiRequests(Cookie session) {
        return List.of(
                get("/api/admin/__probe"),
                get("/api/admin/xyz"),
                get("/api/admin/reports/1"),
                TestLogin.withCsrf(post("/api/admin/__probe"), session),
                post("/api/admin/xyz"));
    }

    @Test
    void 비회원은_화면_경로_있는_주소_없는_주소_모두_같은_401_셸() throws Exception {
        Shape first = null;
        for (MockHttpServletRequestBuilder request : pageRequests(null)) {
            Shape shape = send(request, null);
            if (first == null) {
                first = shape;
                assertThat(first.status()).isEqualTo(401);
                assertThat(first.contentType()).startsWith("text/html");
                assertThat(first.cacheControl()).isEqualTo("private, no-store");
                assertThat(first.body()).contains("<div id=\"root\"").doesNotContain("og:title");
            }
            assertThat(shape).isEqualTo(first);
        }
    }

    @Test
    void 비회원은_API_경로_있는_주소_없는_주소_모두_같은_401_LOGIN_REQUIRED() throws Exception {
        for (MockHttpServletRequestBuilder request : apiRequests(null)) {
            Shape shape = send(request, null);
            assertThat(shape.status()).isEqualTo(401);
            assertThat(shape.body()).isEqualTo(LOGIN_REQUIRED);
        }
    }

    @Test
    void 일반_회원은_세_경로_모두_공통_404() throws Exception {
        Cookie member = TestLogin.loginAs(mockMvc, members().member().create());
        byte[] notFoundPage = notFoundPageRenderer.render().getBody();

        for (MockHttpServletRequestBuilder request : pageRequests(member)) {
            Shape shape = send(request, member);
            assertThat(shape.status()).isEqualTo(404);
            assertThat(shape.contentType()).startsWith("text/html");
            assertThat(shape.cacheControl()).isEqualTo("private, no-store");
            assertThat(shape.body().getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .isEqualTo(notFoundPage);
        }
        for (MockHttpServletRequestBuilder request : apiRequests(member)) {
            Shape shape = send(request, member);
            assertThat(shape.status()).isEqualTo(404);
            assertThat(shape.body()).isEqualTo(NOT_FOUND);
            assertThat(shape.cacheControl()).isEqualTo("private, no-store");
        }
    }

    @Test
    void 인증_전_정지_회원도_일반_회원과_같은_404() throws Exception {
        long unverified = members().member().emailVerified(false).create();
        long suspended = members().member().create();
        members().suspend(suspended, java.time.Instant.now().plusSeconds(86_400), "관리자 경로");
        Cookie member = TestLogin.loginAs(mockMvc, members().member().create());
        Shape expected = send(get("/api/admin/__probe"), member);

        for (long id : new long[] {unverified, suspended}) {
            Cookie session = TestLogin.loginAs(mockMvc, id);
            assertThat(send(get("/api/admin/__probe"), session)).isEqualTo(expected);
            assertThat(send(get("/api/admin/xyz"), session)).isEqualTo(expected);
        }
    }

    @Test
    void 관리자는_있는_경로_200_없는_경로_404() throws Exception {
        Cookie admin = TestLogin.loginAs(mockMvc, members().member().role("ADMIN").create());

        Shape probe = send(get("/api/admin/__probe"), admin);
        assertThat(probe.status()).isEqualTo(200);
        assertThat(probe.body()).contains("\"admin\":true");
        assertThat(send(TestLogin.withCsrf(post("/api/admin/__probe"), admin), admin).status())
                .isEqualTo(200);

        Shape missing = send(get("/api/admin/xyz"), admin);
        assertThat(missing.status()).isEqualTo(404);
        assertThat(missing.body()).isEqualTo(NOT_FOUND);
    }

    @Test
    void 관리자라도_CSRF_토큰_없는_쓰기는_403_CSRF_REJECTED() throws Exception {
        Cookie admin = TestLogin.loginAs(mockMvc, members().member().role("ADMIN").create());

        Shape shape = send(post("/api/admin/__probe"), admin);

        assertThat(shape.status()).isEqualTo(403);
        assertThat(shape.body()).contains("\"code\":\"CSRF_REJECTED\"");
    }

    @Test
    void 관리자에서_일반_회원으로_바뀌면_남은_세션도_바로_404() throws Exception {
        long id = members().member().role("ADMIN").create();
        Cookie session = TestLogin.loginAs(mockMvc, id);
        assertThat(send(get("/api/admin/__probe"), session).status()).isEqualTo(200);

        jdbc.update("UPDATE member SET role = 'USER' WHERE id = ?", id);

        assertThat(send(get("/api/admin/__probe"), session).status()).isEqualTo(404);
    }

    @Test
    void 관리자_경로가_아닌_곳은_그대로() throws Exception {
        Cookie member = TestLogin.loginAs(mockMvc, members().member().create());
        for (Cookie session : Arrays.asList(null, member)) {
            assertThat(send(get("/api/administrator"), session).status()).isNotIn(401);
            assertThat(send(get("/api/__test/viewer"), session).status()).isEqualTo(200);
        }
    }
}
