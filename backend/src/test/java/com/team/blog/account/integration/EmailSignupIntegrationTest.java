package com.team.blog.account.integration;

import static com.team.blog.account.integration.SignupRequests.CURRENT_VERSION;
import static com.team.blog.account.integration.SignupRequests.awaitMailCount;
import static com.team.blog.account.integration.SignupRequests.body;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.RedisOutage;
import jakarta.servlet.http.Cookie;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.web.servlet.MvcResult;

/** 이메일 가입 (US1 #1·#2·#7, FR-002~005·010·013·016~026, R-09·R-10, SC-001). */
class EmailSignupIntegrationTest extends IntegrationTestBase {

    private static final String EMAIL = "kim755030@naver.com";

    @Test
    void signupCreatesUnverifiedAccountRecordsAgreementsSendsMailAndLogsIn() throws Exception {
        MvcResult result =
                mockMvc.perform(body(" Kim755030@Naver.com ", "kim755030", "김민서").request())
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.handle").value("kim755030"))
                        .andExpect(jsonPath("$.nickname").value("김민서"))
                        .andExpect(jsonPath("$.emailVerified").value(false))
                        .andReturn();

        Map<String, Object> identity =
                jdbc.queryForMap(
                        "SELECT member_id, provider, provider_user_id, email, password_hash,"
                                + " email_verified_at FROM auth_identity");
        assertThat(identity.get("provider")).isEqualTo("LOCAL");
        assertThat(identity.get("provider_user_id")).isEqualTo(EMAIL);
        assertThat(identity.get("email")).isEqualTo(EMAIL);
        assertThat(identity.get("email_verified_at")).isNull();
        assertThat((String) identity.get("password_hash"))
                .startsWith("$2")
                .doesNotContain(SignupRequests.PASSWORD);
        long memberId = ((Number) identity.get("member_id")).longValue();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT nickname_changed_at IS NULL FROM member WHERE id = ?",
                                Boolean.class,
                                memberId))
                .isTrue();

        List<Map<String, Object>> agreements =
                jdbc.queryForList(
                        "SELECT type, version, agreed_at FROM member_agreement WHERE member_id = ?"
                                + " ORDER BY type",
                        memberId);
        assertThat(agreements).hasSize(2);
        assertThat(agreements).extracting(r -> r.get("type")).containsExactly("PRIVACY", "TERMS");
        assertThat(agreements)
                .allSatisfy(r -> assertThat(r.get("version")).isEqualTo(CURRENT_VERSION));
        assertThat(agreements).allSatisfy(r -> assertThat(r.get("agreed_at")).isNotNull());

        awaitMailCount(mailSender, EMAIL, 1);
        assertThat(mailSender.lastTextFor(EMAIL).orElseThrow()).contains("/verify-email?token=");
        assertThat(mailSender.lastTokenFor(EMAIL)).isPresent();

        Cookie session = result.getResponse().getCookie("SESSION");
        assertThat(session).isNotNull();
        mockMvc.perform(get("/api/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.handle").value("kim755030"))
                .andExpect(jsonPath("$.nickname").value("김민서"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.provider").value("LOCAL"))
                .andExpect(jsonPath("$.emailVerified").value(false))
                .andExpect(jsonPath("$.reagreementRequired").value(false))
                .andExpect(jsonPath("$.profileImageUrl").doesNotExist());
    }

    @Test
    void sameEmailIgnoringCaseAndSpacesIsRejected() throws Exception {
        mockMvc.perform(body(EMAIL, "kim755030", "김민서").request()).andExpect(status().isCreated());

        mockMvc.perform(body("  KIM755030@naver.COM", "other_handle", "다른사람").request())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("email"))
                .andExpect(jsonPath("$.errors[0].code").value("EMAIL_ALREADY_REGISTERED"))
                .andExpect(jsonPath("$.errors[0].message").value("이미 가입된 이메일이에요. [로그인] [비밀번호 찾기]"));
        assertThat(count("member")).isEqualTo(1);
        assertThat(count("auth_identity")).isEqualTo(1);
    }

    @ParameterizedTest(name = "[{index}] {0} / {1} → {3}")
    @CsvSource(
            delimiter = '|',
            value = {
                "abc12345 | abc12345 | password | PASSWORD_MISSING_CHAR_TYPE",
                "Abcdefg1!Abcdefg1 | Abcdefg1!Abcdefg1 | password | PASSWORD_INVALID_LENGTH",
                "Ab1!xyz | Ab1!xyz | password | PASSWORD_INVALID_LENGTH",
                "Kim755030!x | Kim755030!x | password | PASSWORD_CONTAINS_EMAIL",
                "Password1! | Password1! | password | PASSWORD_TOO_COMMON",
                "Abcd 123! | Abcd 123! | password | PASSWORD_INVALID_CHAR",
                "Blog#2026a | Blog#2026b | passwordConfirm | PASSWORD_CONFIRM_MISMATCH",
            })
    void passwordRuleViolationsCreateNothing(
            String password, String confirm, String field, String code) throws Exception {
        mockMvc.perform(
                        body(EMAIL, "kim755030", "김민서")
                                .with("password", password)
                                .with("passwordConfirm", confirm)
                                .request())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field == '" + field + "')].code", hasItem(code)));
        assertNothingCreated();
    }

    @Test
    void agreementVersionMustBeCurrent() throws Exception {
        mockMvc.perform(
                        body(EMAIL, "kim755030", "김민서")
                                .with(
                                        "agreements",
                                        Map.of(
                                                "termsVersion",
                                                "2025-01-01",
                                                "privacyVersion",
                                                CURRENT_VERSION))
                                .request())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("agreements"))
                .andExpect(jsonPath("$.errors[0].code").value("AGREEMENT_VERSION_MISMATCH"));
        assertNothingCreated();
    }

    @Test
    void agreementsAreRequired() throws Exception {
        mockMvc.perform(body(EMAIL, "kim755030", "김민서").without("agreements").request())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("agreements"))
                .andExpect(jsonPath("$.errors[0].code").value("AGREEMENT_REQUIRED"));
        mockMvc.perform(
                        body(EMAIL, "kim755030", "김민서")
                                .with("agreements", Map.of("termsVersion", CURRENT_VERSION))
                                .request())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("AGREEMENT_REQUIRED"));
        assertNothingCreated();
    }

    @Test
    void returnsAllFieldErrorsOfOneRequestAtOnce() throws Exception {
        mockMvc.perform(
                        body("not-an-email", "Kim-755030", "ㅋㅋ")
                                .with("password", "abc")
                                .with("passwordConfirm", "abc")
                                .without("agreements")
                                .request())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(
                        jsonPath("$.errors[*].field")
                                .value(
                                        containsInAnyOrder(
                                                "email",
                                                "handle",
                                                "password",
                                                "password",
                                                "nickname",
                                                "agreements")))
                .andExpect(
                        jsonPath(
                                "$.errors[?(@.field == 'email')].code",
                                hasItem("EMAIL_INVALID_FORMAT")))
                .andExpect(
                        jsonPath(
                                "$.errors[?(@.field == 'handle')].code",
                                hasItem("HANDLE_INVALID_FORMAT")))
                .andExpect(
                        jsonPath(
                                "$.errors[?(@.field == 'nickname')].code",
                                hasItem("NICKNAME_INVALID_FORMAT")));
        assertNothingCreated();
    }

    @Test
    void emailLongerThan254IsInvalidFormat() throws Exception {
        String longEmail = "a".repeat(243) + "@example.com"; // 255자
        assertThat(longEmail).hasSize(255);
        mockMvc.perform(body(longEmail, "kim755030", "김민서").request())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("email"))
                .andExpect(jsonPath("$.errors[0].code").value("EMAIL_INVALID_FORMAT"));
        assertNothingCreated();
    }

    @Test
    void handleRulesAreCheckedForEmailSignup() throws Exception {
        mockMvc.perform(body(EMAIL, "admin", "김민서").request())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("HANDLE_RESERVED"));
        mockMvc.perform(body(EMAIL, "go-kim755030", "김민서").request())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("HANDLE_PREFIX_MISMATCH"));
        assertNothingCreated();
    }

    @Test
    void takenHandleIsRejectedWithSuggestion() throws Exception {
        members().member().handle("kim755030").create();
        mockMvc.perform(body(EMAIL, "kim755030", "김민서").request())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("handle"))
                .andExpect(jsonPath("$.errors[0].code").value("HANDLE_DUPLICATE"))
                .andExpect(jsonPath("$.details.handleSuggestion").value("kim755030_2"));
    }

    @Test
    void nicknameIsNormalizedToNfcAndDuplicateIgnoresCase() throws Exception {
        String nfd = Normalizer.normalize("김민서", Normalizer.Form.NFD);
        mockMvc.perform(body(EMAIL, "kim755030", " " + nfd + " ").request())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nickname").value("김민서"));
        assertThat(jdbc.queryForObject("SELECT nickname FROM member", String.class))
                .isEqualTo("김민서");

        members().member().nickname("Kim").create();
        mockMvc.perform(body("other@naver.com", "other_one", "kim").request())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("nickname"))
                .andExpect(jsonPath("$.errors[0].code").value("NICKNAME_DUPLICATE"));
    }

    @Test
    void concurrentSignupsWithSameEmailCreateOneAccount() throws Exception {
        List<Integer> statuses =
                runConcurrently(
                        10,
                        i ->
                                mockMvc.perform(body(EMAIL, "race_handle_" + i, "경주" + i).request())
                                        .andReturn());
        assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
        assertThat(statuses).filteredOn(s -> s == 400).hasSize(9);
        assertThat(count("auth_identity")).isEqualTo(1);
        assertThat(count("member")).isEqualTo(1);
        assertThat(count("member_agreement")).isEqualTo(2);
    }

    @Test
    void concurrentSignupsWithSameHandleCreateOneAndSuggestAlternative() throws Exception {
        List<MvcResult> results = new ArrayList<>();
        List<Integer> statuses =
                runConcurrently(
                        10,
                        i -> {
                            MvcResult r =
                                    mockMvc.perform(
                                                    body(
                                                                    "race" + i + "@x.com",
                                                                    "racehandle",
                                                                    "경주" + i)
                                                            .request())
                                            .andReturn();
                            synchronized (results) {
                                results.add(r);
                            }
                            return r;
                        });
        assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
        assertThat(count("member")).isEqualTo(1);
        for (MvcResult r : results) {
            if (r.getResponse().getStatus() == 400) {
                String json = r.getResponse().getContentAsString();
                assertThat(json).contains("HANDLE_DUPLICATE").contains("racehandle_2");
            }
        }
    }

    @Test
    void redisOutageRejectsSignupWith503AndCreatesNothing() throws Exception {
        try (RedisOutage ignored = RedisOutage.start()) {
            mockMvc.perform(body(EMAIL, "kim755030", "김민서").request())
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("TEMPORARILY_UNAVAILABLE"))
                    .andExpect(header().string("Retry-After", "30"));
        }
        assertNothingCreated();
    }

    @Test
    void currentAgreementsArePublic() throws Exception {
        mockMvc.perform(get("/api/agreements/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.terms.version").value(CURRENT_VERSION))
                .andExpect(jsonPath("$.terms.effectiveDate").value("2026-10-07"))
                .andExpect(jsonPath("$.terms.path").value("/terms"))
                .andExpect(jsonPath("$.privacy.version").value(CURRENT_VERSION))
                .andExpect(jsonPath("$.privacy.path").value("/privacy"));
    }

    @Test
    void meRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_REQUIRED"));
    }

    private void assertNothingCreated() {
        assertThat(count("member")).isZero();
        assertThat(count("auth_identity")).isZero();
        assertThat(count("member_agreement")).isZero();
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    @FunctionalInterface
    private interface Attempt {
        MvcResult run(int index) throws Exception;
    }

    private static List<Integer> runConcurrently(int n, Attempt attempt) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        try {
            java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                int index = i;
                Callable<Integer> task =
                        () -> {
                            start.await();
                            return attempt.run(index).getResponse().getStatus();
                        };
                futures.add(pool.submit(task));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : futures) {
                statuses.add(f.get());
            }
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }
}
