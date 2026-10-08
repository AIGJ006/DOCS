package com.team.blog.account.integration;

import static com.team.blog.tag.support.TagApi.body;
import static com.team.blog.tag.support.TagApi.read;
import static com.team.blog.tag.support.TagApi.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.team.blog.account.application.purge.WithdrawalPurgeRunner;
import com.team.blog.account.support.WithdrawalPurgeProbe;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.MemberFixtures;
import com.team.blog.support.TestLogin;
import com.team.blog.support.ai.FakeAiConfiguration.FakeAi;
import com.team.blog.support.fixture.PostFixtures;
import com.team.blog.tag.support.AiSuggestApi;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * AI 외부 전송 동의 API (013 T033, US2 #1~#4, SC-001). "설정 버전을 올리면 다시 409"는 새 컨텍스트 대신 저장 버전을 옛 값으로 두어 같은
 * 판정(저장 버전 ≠ 현재 버전)을 만든다.
 */
class AiConsentIT extends IntegrationTestBase {

    private static final String OLD = "2026-09-01";
    private static final String URL = "/api/me/agreements/ai";

    @Autowired private FakeAi fakeAi;
    @Autowired private WithdrawalPurgeRunner purgeRunner;
    @Autowired private WithdrawalPurgeProbe purgeProbe;

    private AiSuggestApi api;

    @BeforeEach
    void setUp() {
        fakeAi.reset();
        api = new AiSuggestApi(mockMvc, jdbc);
    }

    private long publicPost(long memberId) {
        return new PostFixtures(jdbc).create(memberId, PostFixtures.State.PUBLISHED_PUBLIC);
    }

    private MvcResult getConsent(Cookie session) throws Exception {
        return mockMvc.perform(get(URL).cookie(session)).andReturn();
    }

    private MvcResult putConsent(Cookie session, String version) throws Exception {
        return mockMvc.perform(
                        TestLogin.withCsrf(
                                put(URL).contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"version\":\"" + version + "\"}"),
                                session))
                .andReturn();
    }

    private MvcResult deleteConsent(Cookie session) throws Exception {
        return mockMvc.perform(TestLogin.withCsrf(delete(URL), session)).andReturn();
    }

    private Map<String, Object> row(long memberId) {
        List<Map<String, Object>> rows =
                jdbc.queryForList(
                        "SELECT version, agreed_at FROM member_agreement WHERE member_id = ? AND type = 'AI'",
                        memberId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Test
    void 동의_전_추천은_409이고_두_공급자_호출_0() throws Exception {
        long id = members().member().create();
        MvcResult r =
                api.suggest(
                        TestLogin.loginAs(mockMvc, id),
                        publicPost(id),
                        AiSuggestApi.body(List.of()));
        assertThat(status(r)).as(body(r)).isEqualTo(409);
        assertThat((String) read(r, "$.code")).isEqualTo("AI_CONSENT_REQUIRED");
        assertThat(fakeAi.gemini().calls()).isZero();
        assertThat(fakeAi.ollama().calls()).isZero();
    }

    @Test
    void GET은_없음_현재_옛_버전_세_모양() throws Exception {
        long id = members().member().create();
        Cookie s = TestLogin.loginAs(mockMvc, id);

        MvcResult none = getConsent(s);
        assertThat(status(none)).isEqualTo(200);
        assertThat(body(none))
                .isEqualTo(
                        "{\"agreed\":false,\"version\":null,\"currentVersion\":\"2026-10-08\",\"agreedAt\":null}");

        api.consent(id, OLD);
        MvcResult outdated = getConsent(s);
        assertThat((Boolean) read(outdated, "$.agreed")).isFalse();
        assertThat((String) read(outdated, "$.version")).isEqualTo(OLD);
        assertThat((String) read(outdated, "$.currentVersion")).isEqualTo(AiSuggestApi.VERSION);
        assertThat((String) read(outdated, "$.agreedAt")).isNotNull();

        api.consent(id);
        MvcResult agreed = getConsent(s);
        assertThat((Boolean) read(agreed, "$.agreed")).isTrue();
        assertThat((String) read(agreed, "$.version")).isEqualTo(AiSuggestApi.VERSION);
    }

    @Test
    void PUT_현재_버전은_200과_행() throws Exception {
        long id = members().member().create();
        Cookie s = TestLogin.loginAs(mockMvc, id);
        Instant before = Instant.now().minusSeconds(1);

        MvcResult r = putConsent(s, AiSuggestApi.VERSION);

        assertThat(status(r)).as(body(r)).isEqualTo(200);
        assertThat((Boolean) read(r, "$.agreed")).isTrue();
        assertThat((String) read(r, "$.agreedAt")).isNotNull();
        Map<String, Object> row = row(id);
        assertThat(row.get("version")).isEqualTo(AiSuggestApi.VERSION);
        assertThat(((Timestamp) row.get("agreed_at")).toInstant()).isAfter(before);
        // 같은 버전 다시 보내도 200 (행 하나)
        assertThat(status(putConsent(s, AiSuggestApi.VERSION))).isEqualTo(200);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM member_agreement WHERE member_id = ? AND type = 'AI'",
                                Integer.class,
                                id))
                .isEqualTo(1);
    }

    @Test
    void PUT_옛_버전은_400_AGREEMENT_VERSION_MISMATCH() throws Exception {
        long id = members().member().create();
        Cookie s = TestLogin.loginAs(mockMvc, id);
        MvcResult r = putConsent(s, OLD);
        assertThat(status(r)).as(body(r)).isEqualTo(400);
        assertThat((String) read(r, "$.code")).isEqualTo("VALIDATION_FAILED");
        assertThat((String) read(r, "$.errors[0].field")).isEqualTo("version");
        assertThat((String) read(r, "$.errors[0].code")).isEqualTo("AGREEMENT_VERSION_MISMATCH");
        assertThat(row(id)).isNull();
        // 본문 없음도 400
        MvcResult empty =
                mockMvc.perform(
                                TestLogin.withCsrf(
                                        put(URL).contentType(MediaType.APPLICATION_JSON)
                                                .content("{}"),
                                        s))
                        .andReturn();
        assertThat(status(empty)).isEqualTo(400);
    }

    @Test
    void 저장_버전이_옛_버전이면_추천은_409_details_version은_현재() throws Exception {
        long id = members().member().create();
        api.consent(id, OLD);
        MvcResult r =
                api.suggest(
                        TestLogin.loginAs(mockMvc, id),
                        publicPost(id),
                        AiSuggestApi.body(List.of()));
        assertThat(status(r)).as(body(r)).isEqualTo(409);
        assertThat((String) read(r, "$.details.version")).isEqualTo(AiSuggestApi.VERSION);
        assertThat(fakeAi.totalCalls()).isZero();
    }

    @Test
    void 동의하면_추천되고_DELETE_뒤에는_다시_409() throws Exception {
        long id = members().member().create();
        Cookie s = TestLogin.loginAs(mockMvc, id);
        long postId = publicPost(id);
        assertThat(status(putConsent(s, AiSuggestApi.VERSION))).isEqualTo(200);
        MvcResult ok = api.suggest(s, postId, AiSuggestApi.body(List.of()));
        assertThat(status(ok)).as(body(ok)).isEqualTo(200);

        MvcResult revoked = deleteConsent(s);
        assertThat(status(revoked)).isEqualTo(200);
        assertThat((Boolean) read(revoked, "$.agreed")).isFalse();
        assertThat(row(id)).isNull();
        // 없어도 200
        assertThat(status(deleteConsent(s))).isEqualTo(200);

        MvcResult again = api.suggest(s, postId, AiSuggestApi.body(List.of("jpa")));
        assertThat(status(again)).isEqualTo(409);
    }

    @Test
    void 인증_전_회원도_PUT_가능() throws Exception {
        long id = members().member().emailVerified(false).create();
        MvcResult r = putConsent(TestLogin.loginAs(mockMvc, id), AiSuggestApi.VERSION);
        assertThat(status(r)).as(body(r)).isEqualTo(200);
        assertThat(row(id)).isNotNull();
    }

    @Test
    void 정지_회원은_PUT_DELETE_403() throws Exception {
        long id = members().member().create();
        api.consent(id);
        Cookie s = TestLogin.loginAs(mockMvc, id);
        members().suspend(id, Instant.now().plus(Duration.ofDays(3)), "AI 동의 시험");
        MvcResult r = putConsent(s, AiSuggestApi.VERSION);
        assertThat(status(r)).as(body(r)).isEqualTo(403);
        assertThat(status(deleteConsent(s))).isEqualTo(403);
        assertThat(row(id)).isNotNull();
    }

    @Test
    void CSRF_없으면_403_비회원은_401() throws Exception {
        long id = members().member().create();
        Cookie s = TestLogin.loginAs(mockMvc, id);
        MvcResult r =
                mockMvc.perform(
                                put(URL).cookie(s)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"version\":\"" + AiSuggestApi.VERSION + "\"}"))
                        .andReturn();
        assertThat(status(r)).isEqualTo(403);
        assertThat(row(id)).isNull();
        assertThat(status(mockMvc.perform(get(URL)).andReturn())).isEqualTo(401);
    }

    @Test
    void 로그인_재동의_목록에_AI는_없다() throws Exception {
        String email = "ai-consent-login@example.com";
        long id = members().member().email(email).create();
        for (String type : new String[] {"TERMS", "PRIVACY"}) {
            jdbc.update(
                    "INSERT INTO member_agreement (member_id, type, version, agreed_at) VALUES (?, ?, ?, ?)",
                    id,
                    type,
                    SignupRequests.CURRENT_VERSION,
                    Timestamp.from(Instant.now()));
        }
        api.consent(id, OLD); // AI만 옛 버전
        MvcResult r =
                mockMvc.perform(
                                TestLogin.withCsrf(
                                        post("/api/auth/login")
                                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                                .param("email", email)
                                                .param("password", MemberFixtures.DEFAULT_PASSWORD),
                                        null))
                        .andReturn();
        assertThat(status(r)).as(body(r)).isEqualTo(200);
        assertThat((Boolean) read(r, "$.reagreementRequired")).isFalse();
        assertThat(body(r)).doesNotContain("\"AI\"");
    }

    @Test
    void 탈퇴_익명_처리_뒤에도_AI_동의_행은_남는다() {
        // 015 FR-028 (013 T056): 동의 기록은 법령에 따라 보관 — 익명 처리 단계가 지우지 않는다
        long id = members().member().create();
        api.consent(id);
        jdbc.update(
                "UPDATE member SET status = 'WITHDRAWN', withdrawn_at = now() - interval '400 days'"
                        + " WHERE id = ?",
                id);

        purgeProbe.reset();
        // 정리 작업 전체(job.run)는 다른 시험이 남긴 탈퇴 회원·배치 한도에 영향을 받으므로 한 회원만 정리한다
        assertThat(purgeRunner.purgeOne(id, WithdrawalPurgeRunner.Reason.GRACE_EXPIRED))
                .isEqualTo(WithdrawalPurgeRunner.Outcome.PURGED);

        assertThat(
                        jdbc.queryForObject(
                                "SELECT deleted_at IS NOT NULL FROM member WHERE id = ?",
                                Boolean.class,
                                id))
                .isTrue();
        assertThat(row(id)).isNotNull().containsEntry("version", AiSuggestApi.VERSION);
    }
}
