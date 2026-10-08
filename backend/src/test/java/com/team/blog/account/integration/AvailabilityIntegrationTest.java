package com.team.blog.account.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.RedisOutage;
import com.team.blog.support.TestLogin;
import jakarta.servlet.http.Cookie;
import java.text.Normalizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** US3 블로그 주소·닉네임 사용 가능 확인 (FR-016~029, 08 §4-2, 09 §6, quickstart §4-3). */
class AvailabilityIntegrationTest extends IntegrationTestBase {

    private static MockHttpServletRequestBuilder handle(String value) {
        return get("/api/handles/availability").queryParam("handle", value);
    }

    private static MockHttpServletRequestBuilder nickname(String value) {
        return get("/api/nicknames/availability").queryParam("nickname", value);
    }

    private static MockHttpServletRequestBuilder from(
            MockHttpServletRequestBuilder request, String ip) {
        return request.with(
                r -> {
                    r.setRemoteAddr(ip);
                    return r;
                });
    }

    @Test
    @DisplayName("있는 주소 → HANDLE_DUPLICATE + _2 대안, 없는 주소 → 사용 가능")
    void handleDuplicateAndAvailable() throws Exception {
        members().member().handle("kim755030").create();
        mockMvc.perform(handle("kim755030"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.reason").value("HANDLE_DUPLICATE"))
                .andExpect(jsonPath("$.suggestion").value("kim755030_2"));
        mockMvc.perform(handle("kim755031"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.reason").value((Object) null))
                .andExpect(jsonPath("$.suggestion").value((Object) null));
    }

    @ParameterizedTest(name = "[{index}] {0} → {1}")
    @CsvSource(
            delimiter = '|',
            nullValues = "NULL",
            value = {
                "admin | HANDLE_RESERVED | admin_2",
                "go-admin | HANDLE_RESERVED | go-admin_2",
                "Kim755030 | HANDLE_INVALID_FORMAT | NULL",
                "kim-min | HANDLE_INVALID_FORMAT | NULL",
                "ab | HANDLE_INVALID_FORMAT | NULL",
                "xx-abc | HANDLE_INVALID_FORMAT | NULL",
            })
    void handleRejected(String value, String reason, String suggestion) throws Exception {
        mockMvc.perform(handle(value))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.reason").value(reason))
                .andExpect(jsonPath("$.suggestion").value(suggestion));
    }

    @ParameterizedTest(name = "[{index}] {0} → {1}")
    @CsvSource(
            delimiter = '|',
            value = {
                "ㅋㅋ | NICKNAME_INVALID_FORMAT",
                "김 민서 | NICKNAME_INVALID_FORMAT",
                "12345 | NICKNAME_LETTER_REQUIRED",
                "관리자김 | NICKNAME_RESERVED",
                "시1발 | NICKNAME_BANNED_WORD",
                "kim | NICKNAME_DUPLICATE",
            })
    void nicknameRejected(String value, String code) throws Exception {
        members().member().nickname("Kim").create();
        String body =
                mockMvc.perform(nickname(value))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.available").value(false))
                        .andExpect(jsonPath("$.code").value(code))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(body).doesNotContain("시발"); // 금칙어 응답에 단어를 싣지 않는다
    }

    @Test
    @DisplayName("시발점은 통과한다(09 예외 단어)")
    void allowedNickname() throws Exception {
        mockMvc.perform(nickname("시발점"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.code").value((Object) null));
    }

    @Test
    @DisplayName("로그인한 본인 닉네임의 대소문자만 바꾼 값은 사용 가능(자기 제외)")
    void selfExcluded() throws Exception {
        long me = members().member().nickname("Kim").create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        mockMvc.perform(nickname("KIM").cookie(session))
                .andExpect(jsonPath("$.available").value(true));
        mockMvc.perform(nickname("KIM")).andExpect(jsonPath("$.code").value("NICKNAME_DUPLICATE"));
    }

    @Test
    @DisplayName("같은 IP 1분 31번째 → 429 + Retry-After, 다른 IP는 영향 없음, 닉네임 확인은 따로 센다")
    void rateLimitedPerIp() throws Exception {
        for (int i = 0; i < 30; i++) {
            mockMvc.perform(from(handle("user" + i + "aa"), "198.51.100.7"))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(from(handle("another"), "198.51.100.7"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
        mockMvc.perform(from(handle("another"), "198.51.100.8")).andExpect(status().isOk());
        mockMvc.perform(from(nickname("김민서"), "198.51.100.7")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Redis가 멈추면 요청 제한을 건너뛰어 31번 모두 200")
    void redisOutagePassesRateLimit() throws Exception {
        try (RedisOutage outage = RedisOutage.start()) {
            for (int i = 0; i < 31; i++) {
                mockMvc.perform(from(handle("user" + i + "bb"), "198.51.100.9"))
                        .andExpect(status().isOk());
            }
        }
    }

    // ---- 가입 요청에서 다시 판정 ----

    @Test
    @DisplayName("US3 #5 예약어 주소로 가입 → 400 HANDLE_RESERVED")
    void signupWithReservedHandle() throws Exception {
        mockMvc.perform(SignupRequests.body("a1@example.com", "admin", "새회원").request())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("handle"))
                .andExpect(jsonPath("$.errors[0].code").value("HANDLE_RESERVED"));
    }

    @Test
    @DisplayName("US3 #8 금칙어 닉네임 가입 → 400 NICKNAME_BANNED_WORD")
    void signupWithBannedNickname() throws Exception {
        mockMvc.perform(SignupRequests.body("a2@example.com", "newbie2", "시1발").request())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("nickname"))
                .andExpect(jsonPath("$.errors[0].code").value("NICKNAME_BANNED_WORD"));
    }

    @Test
    @DisplayName("US3 #9 NFD 닉네임으로 가입하면 NFC로 저장한다")
    void signupStoresNfc() throws Exception {
        String nfd = Normalizer.normalize("김민서", Normalizer.Form.NFD);
        mockMvc.perform(SignupRequests.body("a3@example.com", "newbie3", nfd).request())
                .andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT nickname FROM member", String.class))
                .isEqualTo("김민서");
    }
}
