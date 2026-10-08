package com.team.blog.account.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.account.infra.security.GitHubUserClient.GitHubEmail;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.json.JsonMapper;

/**
 * US2 소셜 가입·로그인 (FR-003·008·030~033, R-06~R-08·R-20, SC-012, quickstart §4-11). 실제 OAuth2 콜백 필터를
 * {@link FakeSocialProvider}(가짜 토큰 교환·ID 토큰·GitHub API)로 돌린다.
 */
class SocialSignupIntegrationTest extends IntegrationTestBase {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String VERSION = SignupRequests.CURRENT_VERSION;

    @Autowired FakeSocialProvider provider;

    // ---- #1 처음 Google 사용자: 계정 없이 대기 → 마무리 화면 → 가입 ----

    @Test
    @DisplayName("#1 처음 Google 로그인은 계정을 만들지 않고 대기 정보만 두며, 마무리하면 계정·동의 2행이 생기고 로그인된다")
    void firstGoogleLoginKeepsPendingThenCompletes() throws Exception {
        String code = code();
        provider.google(
                code,
                "google-sub-1",
                "Alice.K@gmail.com",
                true,
                "앨리스",
                "https://lh3.googleusercontent.com/a/ACg8abc=s96-c");

        Flow flow = start("google", "/@someone/posts/1");
        MockHttpServletResponse callback = flow.callback(code);
        assertThat(callback.getStatus()).isEqualTo(302);
        assertThat(callback.getRedirectedUrl()).isEqualTo("/signup/social");
        assertThat(count("member")).isZero();

        mockMvc.perform(get("/api/auth/social-signup").cookie(flow.session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("GOOGLE"))
                .andExpect(jsonPath("$.handlePrefix").value("go-"))
                .andExpect(jsonPath("$.suggestedHandleBody").value("alice_k"))
                .andExpect(jsonPath("$.suggestedNickname").value("앨리스"))
                .andExpect(jsonPath("$.email").value("alice.k@gmail.com"))
                .andExpect(jsonPath("$.emailRequired").value(false))
                .andExpect(jsonPath("$.existingAccountNotice").value(false))
                .andExpect(
                        jsonPath("$.profilePhotoUrl")
                                .value("https://lh3.googleusercontent.com/a/ACg8abc=s256-c"))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty());
        // 대기 중에는 익명이다
        mockMvc.perform(get("/api/me").cookie(flow.session)).andExpect(status().isUnauthorized());

        MvcResult done =
                mockMvc.perform(
                                completeRequest(
                                        flow.session,
                                        Map.of(
                                                "handleBody",
                                                "alice_k",
                                                "nickname",
                                                "앨리스",
                                                "useProfilePhoto",
                                                true)))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.handle").value("go-alice_k"))
                        .andExpect(jsonPath("$.nickname").value("앨리스"))
                        .andExpect(jsonPath("$.emailVerified").value(true))
                        .andExpect(
                                jsonPath("$.profilePhotoUrl")
                                        .value(
                                                "https://lh3.googleusercontent.com/a/ACg8abc=s256-c"))
                        .andExpect(jsonPath("$.redirectTo").value("/@someone/posts/1"))
                        .andReturn();
        Cookie loggedIn = sessionOf(done.getResponse(), flow.session);
        assertThat(TestLogin.sessionId(loggedIn)).isNotEqualTo(TestLogin.sessionId(flow.session));

        mockMvc.perform(get("/api/me").cookie(loggedIn))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.handle").value("go-alice_k"));
        long memberId = jdbc.queryForObject("SELECT id FROM member", Long.class);
        assertThat(
                        jdbc.queryForMap(
                                "SELECT provider, provider_user_id, email, email_verified_at IS NOT NULL AS verified,"
                                        + " password_hash FROM auth_identity WHERE member_id = ?",
                                memberId))
                .containsEntry("provider", "GOOGLE")
                .containsEntry("provider_user_id", "google-sub-1")
                .containsEntry("email", "alice.k@gmail.com")
                .containsEntry("verified", true)
                .containsEntry("password_hash", null);
        assertThat(
                        jdbc.queryForList(
                                "SELECT type || ':' || version FROM member_agreement WHERE member_id = ?"
                                        + " ORDER BY type",
                                String.class,
                                memberId))
                .containsExactly("PRIVACY:" + VERSION, "TERMS:" + VERSION);
        // SC-012: 소셜 사진 주소는 어떤 테이블에도 없다
        assertThat(tablesContaining("googleusercontent")).isEmpty();
        // 대기 정보는 지워졌다
        mockMvc.perform(get("/api/auth/social-signup").cookie(loggedIn))
                .andExpect(status().isGone());
    }

    @Test
    @DisplayName("사진 사용을 끄면 가입 응답에 사진 주소가 없다")
    void noPhotoWhenNotRequested() throws Exception {
        String code = code();
        provider.google(
                code,
                "google-sub-np",
                "np@gmail.com",
                true,
                "노사진",
                "https://lh3.googleusercontent.com/a/x");
        Flow flow = start("google", null);
        flow.callback(code);
        mockMvc.perform(
                        completeRequest(
                                flow.session,
                                Map.of(
                                        "handleBody",
                                        "np_user",
                                        "nickname",
                                        "노사진",
                                        "useProfilePhoto",
                                        false)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.profilePhotoUrl").doesNotExist())
                .andExpect(jsonPath("$.redirectTo").value("/"));
    }

    // ---- #2 10분 만료 ----

    @Test
    @DisplayName("#2 대기 정보가 10분을 넘기면 410 SOCIAL_SIGNUP_EXPIRED, 계정을 만들지 않는다")
    void pendingExpiresAfterTenMinutes() throws Exception {
        String code = code();
        provider.google(code, "google-sub-2", "late@gmail.com", true, "늦은사람", null);
        Flow flow = start("google", null);
        flow.callback(code);

        mockMvc.perform(TestLogin.withCsrf(post("/test/social-signup/age/11"), flow.session))
                .andExpect(status().isNoContent());

        mockMvc.perform(
                        completeRequest(
                                flow.session,
                                Map.of(
                                        "handleBody",
                                        "late",
                                        "nickname",
                                        "늦은사람",
                                        "useProfilePhoto",
                                        false)))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("SOCIAL_SIGNUP_EXPIRED"));
        mockMvc.perform(get("/api/auth/social-signup").cookie(flow.session))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("SOCIAL_SIGNUP_EXPIRED"));
        assertThat(count("member")).isZero();
    }

    @Test
    @DisplayName("대기 정보 없이 마무리하면 410")
    void completeWithoutPending() throws Exception {
        mockMvc.perform(
                        completeRequest(
                                null,
                                Map.of(
                                        "handleBody",
                                        "nobody",
                                        "nickname",
                                        "아무개",
                                        "useProfilePhoto",
                                        false)))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("SOCIAL_SIGNUP_EXPIRED"));
    }

    // ---- #3 이미 연결된 소셜 계정 ----

    @Test
    @DisplayName("#3 GitHub 숫자 ID가 같으면 이메일·login이 바뀌어도 같은 계정으로 로그인하고 보던 페이지로 간다")
    void returningGitHubUserLogsIntoSameAccount() throws Exception {
        long memberId =
                members()
                        .member()
                        .provider("GITHUB")
                        .providerUserId("12345")
                        .email("old@example.com")
                        .handle("gi-octo")
                        .create();
        SignupRequests.agreeCurrent(jdbc, memberId);
        String code = code();
        provider.github(
                code,
                12345,
                "renamed-login",
                "Octo Cat",
                "https://avatars.githubusercontent.com/u/12345?v=4",
                List.of(new GitHubEmail("new@example.com", true, true)));

        Flow flow = start("github", "/settings?tab=1");
        MockHttpServletResponse callback = flow.callback(code);
        assertThat(callback.getStatus()).isEqualTo(302);
        assertThat(callback.getRedirectedUrl()).isEqualTo("/settings?tab=1");
        Cookie session = sessionOf(callback, flow.session);

        mockMvc.perform(get("/api/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.handle").value("gi-octo"));
        assertThat(count("member")).isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT last_login_at IS NOT NULL FROM auth_identity WHERE member_id = ?",
                                Boolean.class,
                                memberId))
                .isTrue();
    }

    @Test
    @DisplayName("외부 주소로 보내는 redirect는 / 로 바꾼다")
    void unsafeRedirectFallsBackToRoot() throws Exception {
        SignupRequests.agreeCurrent(
                jdbc,
                members().member().provider("GOOGLE").providerUserId("google-sub-r").create());
        String code = code();
        provider.google(code, "google-sub-r", "r@gmail.com", true, "리다", null);
        Flow flow = start("google", "//evil.example.com/x");
        assertThat(flow.callback(code).getRedirectedUrl()).isEqualTo("/");
    }

    // ---- #4 같은 이메일의 다른 수단 계정 (FR-033) ----

    @Test
    @DisplayName("#4 같은 이메일의 LOCAL 계정이 있으면 안내(true)만 하고, 가입하면 계정이 2개가 된다")
    void existingAccountNoticeForVerifiedEmail() throws Exception {
        members().member().email("same@example.com").create();
        String code = code();
        provider.google(code, "google-sub-4", "same@example.com", true, "같은메일", null);
        Flow flow = start("google", null);
        flow.callback(code);

        mockMvc.perform(get("/api/auth/social-signup").cookie(flow.session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.existingAccountNotice").value(true))
                .andExpect(jsonPath("$.profilePhotoUrl").value((Object) null));
        mockMvc.perform(
                        completeRequest(
                                flow.session,
                                Map.of(
                                        "handleBody",
                                        "same",
                                        "nickname",
                                        "같은메일",
                                        "useProfilePhoto",
                                        true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.handle").value("go-same"));
        assertThat(count("member")).isEqualTo(2);
    }

    // ---- GitHub 확인된 대표 이메일 없음 (FR-008, R-20) ----

    @Test
    @DisplayName("GitHub에 확인된 대표 이메일이 없으면 이메일을 입력받고, 인증 전 계정 + 인증 메일, 사진은 넘기지 않는다")
    void gitHubWithoutVerifiedEmailRequiresEmail() throws Exception {
        members().member().email("typed@example.com").create();
        String code = code();
        provider.github(
                code,
                777,
                "noemail",
                "No Mail",
                "https://avatars.githubusercontent.com/u/777?v=4",
                List.of(
                        new GitHubEmail("unverified@example.com", true, false),
                        new GitHubEmail("other@example.com", false, true)));
        Flow flow = start("github", null);
        flow.callback(code);

        mockMvc.perform(get("/api/auth/social-signup").cookie(flow.session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("GITHUB"))
                .andExpect(jsonPath("$.handlePrefix").value("gi-"))
                .andExpect(jsonPath("$.emailRequired").value(true))
                .andExpect(jsonPath("$.email").value((Object) null))
                .andExpect(jsonPath("$.existingAccountNotice").value(false))
                .andExpect(jsonPath("$.profilePhotoUrl").value((Object) null))
                .andExpect(jsonPath("$.suggestedNickname").value("NoMail"));

        // 이메일 없이 → 칸 오류
        mockMvc.perform(
                        completeRequest(
                                flow.session,
                                Map.of(
                                        "handleBody",
                                        "noemail",
                                        "nickname",
                                        "NoMail",
                                        "useProfilePhoto",
                                        true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("email"))
                .andExpect(jsonPath("$.errors[0].code").value("EMAIL_INVALID_FORMAT"));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("handleBody", "noemail");
        body.put("nickname", "NoMail");
        body.put("email", " Typed@Example.com ");
        body.put("useProfilePhoto", true);
        mockMvc.perform(completeRequest(flow.session, body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.handle").value("gi-noemail"))
                .andExpect(jsonPath("$.emailVerified").value(false))
                .andExpect(jsonPath("$.profilePhotoUrl").doesNotExist());
        assertThat(
                        jdbc.queryForMap(
                                "SELECT provider_user_id, email, email_verified_at FROM auth_identity"
                                        + " WHERE provider = 'GITHUB'"))
                .containsEntry("provider_user_id", "777")
                .containsEntry("email", "typed@example.com")
                .containsEntry("email_verified_at", null);
        SignupRequests.awaitMailCount(mailSender, "typed@example.com", 1);
    }

    // ---- 거부되는 콜백 ----

    @Test
    @DisplayName("Google email_verified=false는 거부하고 /login?error=social로 보내며 오류는 한 번만 읽힌다")
    void unverifiedGoogleEmailRejected() throws Exception {
        String code = code();
        provider.google(code, "google-sub-u", "u@gmail.com", false, "미인증", null);
        Flow flow = start("google", null);
        MockHttpServletResponse callback = flow.callback(code);
        assertThat(callback.getRedirectedUrl()).isEqualTo("/login?error=social");
        Cookie session = sessionOf(callback, flow.session);

        mockMvc.perform(get("/api/auth/social-signup").cookie(session))
                .andExpect(status().isGone());
        mockMvc.perform(get("/api/auth/social-login-error").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SOCIAL_EMAIL_NOT_VERIFIED"));
        mockMvc.perform(get("/api/auth/social-login-error").cookie(session))
                .andExpect(status().isNoContent());
        assertThat(count("member")).isZero();
    }

    @Test
    @DisplayName("사진 주소가 허용 호스트·HTTPS가 아니면 넘기지 않는다")
    void photoHostMustBeAllowedHttps() throws Exception {
        for (String picture :
                List.of(
                        "https://evil.example.com/a.png",
                        "http://lh3.googleusercontent.com/a/x=s96-c",
                        "https://lh3.googleusercontent.com.evil.example/a",
                        "javascript:alert(1)")) {
            String code = code();
            provider.google(code, "sub-" + code, code + "@gmail.com", true, "사진", picture);
            Flow flow = start("google", null);
            flow.callback(code);
            mockMvc.perform(get("/api/auth/social-signup").cookie(flow.session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.profilePhotoUrl").value((Object) null));
        }
        // GitHub 사진은 &s=256
        String code = code();
        provider.github(
                code,
                99,
                "pic",
                null,
                "https://avatars.githubusercontent.com/u/99?v=4",
                List.of(new GitHubEmail("pic@example.com", true, true)));
        Flow flow = start("github", null);
        flow.callback(code);
        mockMvc.perform(get("/api/auth/social-signup").cookie(flow.session))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.profilePhotoUrl")
                                .value("https://avatars.githubusercontent.com/u/99?v=4&s=256"))
                .andExpect(jsonPath("$.suggestedNickname").value("pic"))
                .andExpect(jsonPath("$.suggestedHandleBody").value("pic"));
    }

    @Test
    @DisplayName("state가 다른 콜백은 거부한다")
    void stateMismatchRejected() throws Exception {
        String code = code();
        provider.google(code, "google-sub-s", "s@gmail.com", true, "상태", null);
        Flow flow = start("google", null);
        MockHttpServletResponse callback = flow.callback(code, "forged-state");
        assertThat(callback.getStatus()).isEqualTo(302);
        assertThat(callback.getRedirectedUrl()).isEqualTo("/login?error=social");
        Cookie session = sessionOf(callback, flow.session);
        mockMvc.perform(get("/api/auth/social-signup").cookie(session))
                .andExpect(status().isGone());
        mockMvc.perform(get("/api/me").cookie(session)).andExpect(status().isUnauthorized());
        assertThat(count("member")).isZero();
    }

    @Test
    @DisplayName("정지 계정 소셜 로그인 → /login?error=social, 오류(ACCOUNT_SUSPENDED + 기한·사유)를 한 번 돌려준다")
    void suspendedSocialLogin() throws Exception {
        long memberId = members().member().provider("GITHUB").providerUserId("4242").create();
        Instant endsAt = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        members().suspend(memberId, endsAt, "스팸");
        String code = code();
        provider.github(
                code, 4242, "spam", null, null, List.of(new GitHubEmail("s@x.com", true, true)));

        Flow flow = start("github", "/write");
        MockHttpServletResponse callback = flow.callback(code);
        assertThat(callback.getRedirectedUrl()).isEqualTo("/login?error=social");
        Cookie session = sessionOf(callback, flow.session);

        mockMvc.perform(get("/api/auth/social-login-error").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"))
                .andExpect(jsonPath("$.details.reason").value("스팸"))
                .andExpect(jsonPath("$.details.endsAt").value(endsAt.toString()));
        mockMvc.perform(get("/api/auth/social-login-error").cookie(session))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/me").cookie(session)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("재동의가 필요한 계정은 소셜 로그인 뒤 /reagree로 간다")
    void reagreementAfterSocialLogin() throws Exception {
        members().member().provider("GOOGLE").providerUserId("google-sub-old").create();
        String code = code();
        provider.google(code, "google-sub-old", "old@gmail.com", true, "예전", null);
        Flow flow = start("google", "/settings");
        assertThat(flow.callback(code).getRedirectedUrl()).isEqualTo("/reagree");
    }

    @Test
    @DisplayName("설정된 소셜 로그인 수단 목록")
    void socialProviders() throws Exception {
        mockMvc.perform(get("/api/auth/social-providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providers[0]").value("GOOGLE"))
                .andExpect(jsonPath("$.providers[1]").value("GITHUB"));
    }

    // ---- 도우미 ----

    private static String code() {
        return UUID.randomUUID().toString();
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    /** 행 전체를 문자열로 바꿔 {@code needle}이 들어 있는 테이블 이름. */
    private List<String> tablesContaining(String needle) {
        List<String> tables =
                jdbc.queryForList(
                        "SELECT tablename FROM pg_tables WHERE schemaname = 'public'",
                        String.class);
        return tables.stream()
                .filter(
                        t ->
                                jdbc.queryForObject(
                                                "SELECT count(*) FROM \""
                                                        + t
                                                        + "\" x WHERE x::text LIKE ?",
                                                Integer.class,
                                                "%" + needle + "%")
                                        > 0)
                .toList();
    }

    private MockHttpServletRequestBuilder completeRequest(
            Cookie session, Map<String, Object> fields) {
        Map<String, Object> body = new LinkedHashMap<>(fields);
        body.put("agreements", Map.of("termsVersion", VERSION, "privacyVersion", VERSION));
        return TestLogin.withCsrf(
                post("/api/auth/social-signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(body)),
                session);
    }

    private static Cookie sessionOf(MockHttpServletResponse response, Cookie previous) {
        Cookie cookie = response.getCookie(TestLogin.SESSION_COOKIE);
        return cookie != null && !cookie.getValue().isEmpty()
                ? new Cookie(TestLogin.SESSION_COOKIE, cookie.getValue())
                : previous;
    }

    /** {@code GET /oauth2/authorization/{id}}로 시작해 state와 세션 쿠키를 받는다. */
    private Flow start(String registrationId, String redirect) throws Exception {
        MockHttpServletRequestBuilder request = get("/oauth2/authorization/{id}", registrationId);
        if (redirect != null) {
            request.queryParam("redirect", redirect);
        }
        MockHttpServletResponse response = mockMvc.perform(request).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(302);
        String location = response.getRedirectedUrl();
        String state =
                UriComponentsBuilder.fromUriString(location)
                        .build()
                        .getQueryParams()
                        .getFirst("state");
        assertThat(state).isNotBlank();
        Cookie session = sessionOf(response, null);
        assertThat(session).isNotNull();
        return new Flow(registrationId, state, session);
    }

    private final class Flow {
        final String registrationId;
        final String state;
        Cookie session;

        Flow(String registrationId, String state, Cookie session) {
            this.registrationId = registrationId;
            this.state = java.net.URLDecoder.decode(state, java.nio.charset.StandardCharsets.UTF_8);
            this.session = session;
        }

        MockHttpServletResponse callback(String code) throws Exception {
            return callback(code, state);
        }

        MockHttpServletResponse callback(String code, String withState) throws Exception {
            MockHttpServletResponse response =
                    mockMvc.perform(
                                    get("/login/oauth2/code/{id}", registrationId)
                                            .queryParam("code", code)
                                            .queryParam("state", withState)
                                            .cookie(session))
                            .andReturn()
                            .getResponse();
            session = sessionOf(response, session);
            return response;
        }
    }
}
