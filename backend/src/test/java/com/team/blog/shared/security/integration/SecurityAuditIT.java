package com.team.blog.shared.security.integration;

import static com.team.blog.post.support.EditorApi.publishBody;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.team.blog.post.support.EditorApi;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * 배포 전 전체 보안 점검 (final-check 3). 기능별 시험이 각자 확인한 것을 앱 전체 기준으로 한 번 더 본다.
 *
 * <ul>
 *   <li>모든 상태 변경 API(POST·PUT·PATCH·DELETE)는 CSRF 토큰 없이 403 {@code CSRF_REJECTED} — 새 API가 빠지지 않게
 *       핸들러 목록에서 직접 고른다.
 *   <li>HTTPS 요청(운영: 신뢰 프록시의 {@code X-Forwarded-Proto: https})에는 HSTS, HTTP에는 없음. 모든 응답에 {@code
 *       X-Frame-Options: DENY}·{@code nosniff}·{@code Referrer-Policy}·CSP {@code frame-ancestors
 *       'none'}.
 *   <li>글 본문·댓글 내용·검색어가 로그에 남지 않는다.
 * </ul>
 */
@ExtendWith(OutputCaptureExtension.class)
class SecurityAuditIT extends IntegrationTestBase {

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping handlerMapping;

    private static final Set<RequestMethod> UNSAFE =
            Set.of(
                    RequestMethod.POST,
                    RequestMethod.PUT,
                    RequestMethod.PATCH,
                    RequestMethod.DELETE);

    /** 시험 전용 컨트롤러(지원 코드)는 운영 앱에 없다. */
    private static boolean testOnly(String path, HandlerMethod method) {
        return path.startsWith("/test/")
                || method.getBeanType().getPackageName().startsWith("com.team.blog.support");
    }

    private static String fill(String pattern) {
        return pattern.replaceAll("\\{handle}", "someone")
                .replaceAll("\\{name}", "spring")
                .replaceAll("\\{[^}]+}", "1");
    }

    @Test
    void 모든_상태_변경_API는_CSRF_토큰_없이_403_CSRF_REJECTED() throws Exception {
        Cookie admin = TestLogin.loginAs(mockMvc, members().member().role("ADMIN").create());
        Set<String> checked = new TreeSet<>();
        List<String> failures = new ArrayList<>();

        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry :
                handlerMapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = entry.getKey();
            for (String pattern : info.getPatternValues()) {
                if (testOnly(pattern, entry.getValue())) {
                    continue;
                }
                for (RequestMethod method : info.getMethodsCondition().getMethods()) {
                    if (!UNSAFE.contains(method)) {
                        continue;
                    }
                    String url = fill(pattern);
                    for (Cookie session : new Cookie[] {null, admin}) {
                        MockHttpServletRequestBuilder request =
                                request(HttpMethod.valueOf(method.name()), url)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{}");
                        if (session != null) {
                            request.cookie(session);
                        }
                        MockHttpServletResponse response =
                                mockMvc.perform(request).andReturn().getResponse();
                        String body = response.getContentAsString(StandardCharsets.UTF_8);
                        // 비회원의 관리자 경로는 CSRF보다 먼저 같은 401로 막힌다(004 AdminPathIT, 주소 존재를 숨김)
                        boolean adminPathForGuest =
                                session == null
                                        && url.startsWith("/api/admin/")
                                        && response.getStatus() == 401
                                        && body.contains("\"LOGIN_REQUIRED\"");
                        boolean csrfRejected =
                                response.getStatus() == 403 && body.contains("\"CSRF_REJECTED\"");
                        if (!csrfRejected && !adminPathForGuest) {
                            failures.add(
                                    method
                                            + " "
                                            + url
                                            + (session == null ? " (비회원)" : " (관리자)")
                                            + " → "
                                            + response.getStatus()
                                            + " "
                                            + body);
                        }
                    }
                    checked.add(method + " " + pattern);
                }
            }
        }
        // 폼 로그인·로그아웃은 컨트롤러가 아니라 보안 필터가 받는다
        for (String path : List.of("/api/auth/login", "/api/auth/logout")) {
            MockHttpServletResponse response =
                    mockMvc.perform(post(path).param("email", "a@b.c").param("password", "x"))
                            .andReturn()
                            .getResponse();
            if (response.getStatus() != 403) {
                failures.add("POST " + path + " → " + response.getStatus());
            }
        }

        assertThat(failures).as("CSRF 없이 통과한 상태 변경 API").isEmpty();
        assertThat(checked).as("상태 변경 API 수 (2026-10-08 기준 54개)").hasSizeGreaterThanOrEqualTo(54);
    }

    @Test
    void HTTPS_요청에만_HSTS가_붙고_모든_응답에_화면_틀_금지_헤더() throws Exception {
        List<MockHttpServletRequestBuilder> requests =
                List.of(
                        get("/api/posts"),
                        get("/"),
                        get("/api/posts/999999"),
                        get("/@nobody"),
                        get("/api/me"));
        for (MockHttpServletRequestBuilder request : requests) {
            MockHttpServletResponse http = mockMvc.perform(request).andReturn().getResponse();
            assertThat(http.getHeader("Strict-Transport-Security")).isNull();
            assertCommonHeaders(http);
        }
        for (String path : List.of("/api/posts", "/", "/api/posts/999999", "/api/me")) {
            MockHttpServletResponse https =
                    mockMvc.perform(get(path).secure(true)).andReturn().getResponse();
            assertThat(https.getHeader("Strict-Transport-Security"))
                    .as(path)
                    .isEqualTo("max-age=31536000 ; includeSubDomains");
            assertCommonHeaders(https);
        }
    }

    private static void assertCommonHeaders(MockHttpServletResponse response) {
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(response.getHeader("Referrer-Policy"))
                .isEqualTo("strict-origin-when-cross-origin");
        assertThat(response.getHeader("Content-Security-Policy"))
                .contains("frame-ancestors 'none'")
                .contains("object-src 'none'")
                .contains("script-src 'self';");
    }

    @Test
    void 글_본문_댓글_내용_검색어는_로그에_남지_않는다(CapturedOutput output) throws Exception {
        long author = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, author);
        EditorApi api = new EditorApi(mockMvc);
        long postId = api.createPostId(session);
        String secretBody = "본문비밀문장QZX" + System.nanoTime();
        String secretTitle = "제목비밀QZX";
        assertThat(
                        EditorApi.status(
                                api.publish(
                                        session,
                                        postId,
                                        publishBody(
                                                secretTitle, secretBody, List.of(), "PUBLIC", 0))))
                .isEqualTo(200);

        String secretComment = "댓글비밀QZX" + System.nanoTime();
        assertThat(
                        mockMvc.perform(
                                        TestLogin.withCsrf(
                                                        post("/api/posts/{id}/comments", postId),
                                                        session)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content("{\"content\":\"" + secretComment + "\"}"))
                                .andReturn()
                                .getResponse()
                                .getStatus())
                .isIn(200, 201);

        String secretQuery = "검색비밀QZX";
        mockMvc.perform(get("/api/search/posts").param("q", secretQuery)).andReturn();
        mockMvc.perform(get("/api/search/people").param("q", secretQuery)).andReturn();
        mockMvc.perform(get("/api/posts/{id}", postId)).andReturn();

        assertThat(output.getAll())
                .doesNotContain(secretBody)
                .doesNotContain(secretTitle)
                .doesNotContain(secretComment)
                .doesNotContain(secretQuery);
    }
}
