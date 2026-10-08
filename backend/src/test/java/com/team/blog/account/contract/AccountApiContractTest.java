package com.team.blog.account.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.MemberFixtures;
import com.team.blog.support.TestLogin;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

/**
 * 주요 성공·오류 응답이 계약({@code specs/001-account-auth/contracts/openapi.yaml})과 맞는가 (T146). 계약 파일은 테스트
 * 리소스로 복사하지 않고 backend 디렉터리 기준 상대 경로로 읽는다(005 계약 시험과 같은 방식, pom 변경 없음). 검사 범위는 {@link
 * OpenApiContract}.
 */
class AccountApiContractTest extends IntegrationTestBase {

    private static final Path CONTRACT =
            Path.of("..", "specs", "001-account-auth", "contracts", "openapi.yaml");
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String CURRENT = "2026-10-07";

    private static OpenApiContract contract;

    @BeforeAll
    static void readContract() throws IOException {
        assertThat(CONTRACT).as("계약 파일 (backend 디렉터리 기준)").exists();
        contract = OpenApiContract.load(CONTRACT);
    }

    @Test
    @DisplayName("검사기 자체 확인: 필수 필드 없음·타입·enum·계약에 없는 필드·날짜 형식을 잡는다")
    void checkerCatchesViolations() {
        assertThat(
                        contract.violations(
                                "/api/auth/signup",
                                "post",
                                201,
                                java.util.Map.of("handle", 1, "nickname", "a", "extra", true)))
                .anySatisfy(v -> assertThat(v).contains("emailVerified"))
                .anySatisfy(v -> assertThat(v).contains("$.handle"))
                .anySatisfy(v -> assertThat(v).contains("extra"));
        assertThat(
                        contract.violations(
                                "/api/members/{handle}/friend",
                                "get",
                                200,
                                java.util.Map.of(
                                        "status",
                                        "BEST",
                                        "lastActive",
                                        java.util.Map.of("bucket", "NOW"))))
                .hasSize(2);
        assertThat(
                        contract.violations(
                                "/api/me/friend-requests",
                                "get",
                                200,
                                java.util.Map.of(
                                        "items",
                                        List.of(
                                                java.util.Map.of(
                                                        "handle", "a",
                                                        "nickname", "b",
                                                        "requestedAt", "어제")),
                                        "nextCursor",
                                        "x")))
                .anySatisfy(v -> assertThat(v).contains("profileImageUrl"))
                .anySatisfy(v -> assertThat(v).contains("date-time"));
    }

    @Test
    @DisplayName("가입: 201 SignupResult, 400 ErrorResponse(칸별 오류)")
    void signup() throws Exception {
        String ok = signupBody("contract@example.com", "contract01", "계약확인");
        conforms("/api/auth/signup", "post", 201, perform(json(post("/api/auth/signup"), ok)));
        String bad = signupBody("not-an-email", "A", "x");
        conforms("/api/auth/signup", "post", 400, perform(json(post("/api/auth/signup"), bad)));
    }

    @Test
    @DisplayName("로그인: 200 LoginResult, 401 INVALID_CREDENTIALS, 403 ACCOUNT_SUSPENDED, 429 잠금")
    void login() throws Exception {
        agreed(members().member().email("login@example.com").create());
        conforms("/api/auth/login", "post", 200, login("login@example.com", PASSWORD));
        conforms("/api/auth/login", "post", 401, login("login@example.com", "Wrong#2026x"));
        long suspended = members().member().email("susp@example.com").create();
        agreed(suspended);
        members().suspend(suspended, Instant.now().plus(3, ChronoUnit.DAYS), "스팸 게시");
        conforms("/api/auth/login", "post", 403, login("susp@example.com", PASSWORD));
        for (int i = 0; i < 5; i++) {
            login("lock@example.com", "Wrong#2026x");
        }
        conforms("/api/auth/login", "post", 429, login("lock@example.com", "Wrong#2026x"));
    }

    @Test
    @DisplayName("공개 조회: 약관 현재 버전, 사용 가능 확인, 소셜 제공자 (CSRF는 본문 없는 204)")
    void publicReads() throws Exception {
        conforms("/api/agreements/current", "get", 200, perform(get("/api/agreements/current")));
        conforms(
                "/api/handles/availability",
                "get",
                200,
                perform(get("/api/handles/availability").queryParam("handle", "admin")));
        conforms(
                "/api/nicknames/availability",
                "get",
                200,
                perform(get("/api/nicknames/availability").queryParam("nickname", "새닉네임")));
        conforms(
                "/api/auth/social-providers",
                "get",
                200,
                perform(get("/api/auth/social-providers")));
        assertThat(perform(get("/api/auth/csrf")).getStatus()).isEqualTo(204);
    }

    @Test
    @DisplayName("me·profile·settings: 200, 401, 400, 409")
    void me() throws Exception {
        long id = members().member().email("me@example.com").create();
        agreed(id);
        Cookie session = sessionOf(login("me@example.com", PASSWORD));

        conforms("/api/me", "get", 200, perform(get("/api/me").cookie(session)));
        conforms("/api/me", "get", 401, perform(get("/api/me")));
        conforms("/api/me/profile", "get", 200, perform(get("/api/me/profile").cookie(session)));
        conforms(
                "/api/me/profile",
                "patch",
                200,
                perform(json(patch("/api/me/profile"), "{\"bio\":\"안녕하세요\"}", session)));
        conforms(
                "/api/me/profile",
                "patch",
                400,
                perform(
                        json(
                                patch("/api/me/profile"),
                                "{\"bio\":\"" + "가".repeat(201) + "\"}",
                                session)));
        conforms(
                "/api/me/profile",
                "patch",
                200,
                perform(json(patch("/api/me/profile"), "{\"nickname\":\"바뀐이름\"}", session)));
        conforms(
                "/api/me/profile",
                "patch",
                409,
                perform(json(patch("/api/me/profile"), "{\"nickname\":\"또바뀐이름\"}", session)));
        conforms("/api/me/profile", "get", 200, perform(get("/api/me/profile").cookie(session)));

        conforms("/api/me/settings", "get", 200, perform(get("/api/me/settings").cookie(session)));
        conforms(
                "/api/me/settings",
                "patch",
                200,
                perform(
                        json(
                                patch("/api/me/settings"),
                                "{\"lastActiveVisible\":false,\"defaultVisibility\":\"PRIVATE\"}",
                                session)));
        conforms(
                "/api/me/settings",
                "patch",
                400,
                perform(
                        json(
                                patch("/api/me/settings"),
                                "{\"defaultVisibility\":\"EVERYONE\"}",
                                session)));
        conforms("/api/me/settings", "get", 401, perform(get("/api/me/settings")));
    }

    @Test
    @DisplayName("친구: 상태 조회·요청·수락·끊기 200(lastActive 있음·없음), 목록 200, 자기 자신 400, 없는 주소 404")
    void friends() throws Exception {
        Instant recent = Instant.now().minus(3, ChronoUnit.DAYS);
        long me = members().member().handle("contractme").lastActive(recent, true).create();
        long other = members().member().handle("contractyou").lastActive(recent, true).create();
        long third = members().member().handle("contractthree").create();
        Cookie mine = TestLogin.loginAs(mockMvc, me);
        Cookie theirs = TestLogin.loginAs(mockMvc, other);
        Cookie thirds = TestLogin.loginAs(mockMvc, third);
        String path = "/api/members/{handle}/friend";

        conforms(path, "get", 200, perform(get("/api/members/contractyou/friend").cookie(mine)));
        conforms(
                path,
                "put",
                200,
                perform(TestLogin.withCsrf(put("/api/members/contractyou/friend"), mine)));
        perform(TestLogin.withCsrf(put("/api/members/contractme/friend"), thirds));
        conforms(
                "/api/me/friend-requests",
                "get",
                200,
                perform(get("/api/me/friend-requests").cookie(mine)));
        MockHttpServletResponse accepted =
                perform(TestLogin.withCsrf(put("/api/members/contractme/friend"), theirs));
        conforms(path, "put", 200, accepted);
        assertThat(accepted.getContentAsString()).contains("lastActive");
        conforms(path, "get", 200, perform(get("/api/members/contractyou/friend").cookie(mine)));
        MockHttpServletResponse friends = perform(get("/api/me/friends").cookie(mine));
        conforms("/api/me/friends", "get", 200, friends);
        assertThat(friends.getContentAsString()).contains("lastActive");
        conforms(
                path,
                "put",
                400,
                perform(TestLogin.withCsrf(put("/api/members/contractme/friend"), mine)));
        conforms(path, "get", 404, perform(get("/api/members/nosuchmember/friend").cookie(mine)));
        conforms(path, "get", 401, perform(get("/api/members/contractyou/friend")));
        conforms(
                path,
                "delete",
                200,
                perform(TestLogin.withCsrf(delete("/api/members/contractyou/friend"), mine)));
        conforms(
                "/api/me/friends",
                "get",
                400,
                perform(get("/api/me/friends").queryParam("cursor", "!!bad").cookie(mine)));
    }

    // ---- 도우미 ----

    private static final String PASSWORD = MemberFixtures.DEFAULT_PASSWORD;

    private void conforms(
            String pathTemplate, String method, int status, MockHttpServletResponse response)
            throws Exception {
        assertThat(response.getStatus())
                .as(method + " " + pathTemplate + " " + response.getContentAsString())
                .isEqualTo(status);
        Object body = JSON.readValue(response.getContentAsString(), Object.class);
        List<String> violations = contract.violations(pathTemplate, method, status, body);
        assertThat(violations)
                .as(
                        method
                                + " "
                                + pathTemplate
                                + " "
                                + status
                                + " "
                                + response.getContentAsString())
                .isEmpty();
    }

    private MockHttpServletResponse perform(MockHttpServletRequestBuilder request)
            throws Exception {
        return mockMvc.perform(request).andReturn().getResponse();
    }

    private MockHttpServletResponse login(String email, String password) throws Exception {
        return perform(
                TestLogin.withCsrf(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("email", email)
                                .param("password", password),
                        null));
    }

    private static MockHttpServletRequestBuilder json(
            MockHttpServletRequestBuilder request, String body) {
        return json(request, body, null);
    }

    private static MockHttpServletRequestBuilder json(
            MockHttpServletRequestBuilder request, String body, Cookie session) {
        return TestLogin.withCsrf(
                request.contentType(MediaType.APPLICATION_JSON).content(body), session);
    }

    private static String signupBody(String email, String handle, String nickname) {
        return "{\"email\":\""
                + email
                + "\",\"handle\":\""
                + handle
                + "\",\"password\":\"Blog#2026a\",\"passwordConfirm\":\"Blog#2026a\",\"nickname\":\""
                + nickname
                + "\",\"agreements\":{\"termsVersion\":\""
                + CURRENT
                + "\",\"privacyVersion\":\""
                + CURRENT
                + "\"}}";
    }

    private void agreed(long memberId) {
        for (String type : List.of("TERMS", "PRIVACY")) {
            jdbc.update(
                    "INSERT INTO member_agreement (member_id, type, version) VALUES (?, ?, ?)",
                    memberId,
                    type,
                    CURRENT);
        }
    }

    private static Cookie sessionOf(MockHttpServletResponse response) {
        Cookie cookie = response.getCookie(TestLogin.SESSION_COOKIE);
        assertThat(cookie).isNotNull();
        return new Cookie(TestLogin.SESSION_COOKIE, cookie.getValue());
    }
}
