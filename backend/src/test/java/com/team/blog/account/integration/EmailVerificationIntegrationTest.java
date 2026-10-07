package com.team.blog.account.integration;

import static com.team.blog.account.integration.SignupRequests.awaitMailCount;
import static com.team.blog.account.integration.SignupRequests.body;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.account.application.EmailVerificationService;
import com.team.blog.shared.error.TemporarilyUnavailableException;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.RedisOutage;
import com.team.blog.support.TestLogin;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** 이메일 인증 (US1 #4·#5, FR-005·006·040, R-11, SC-006). */
class EmailVerificationIntegrationTest extends IntegrationTestBase {

    private static final String EMAIL = "kim755030@naver.com";

    @Autowired private EmailVerificationService emailVerificationService;

    private Cookie session;
    private long memberId;

    @BeforeEach
    void signUp() throws Exception {
        session =
                mockMvc.perform(body(EMAIL, "kim755030", "김민서").request())
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getCookie("SESSION");
        memberId = jdbc.queryForObject("SELECT id FROM member", Long.class);
        awaitMailCount(mailSender, EMAIL, 1);
    }

    @Test
    void confirmVerifiesOnceThenLinkExpires() throws Exception {
        String token = mailSender.lastTokenFor(EMAIL).orElseThrow();

        confirm(token).andExpect(status().isOk()).andExpect(jsonPath("$.verified").value(true));
        assertThat(verifiedAt()).isNotNull();

        confirm(token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LINK_EXPIRED"))
                .andExpect(jsonPath("$.message").value("링크가 만료됐어요. [인증 메일 다시 보내기]"));
    }

    @Test
    void tokenLivesTwentyFourHoursAndExpiredTokenIsRejected() throws Exception {
        String token = mailSender.lastTokenFor(EMAIL).orElseThrow();
        Long ttl = redis.getExpire("auth:verify:" + token);
        assertThat(ttl)
                .isBetween(
                        Duration.ofHours(24).minusMinutes(1).toSeconds(),
                        Duration.ofHours(24).toSeconds());

        redis.delete("auth:verify:" + token); // TTL이 지난 것과 같다
        confirm(token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LINK_EXPIRED"));
        assertThat(verifiedAt()).isNull();
    }

    @Test
    void unknownOrMalformedTokenIsExpired() throws Exception {
        confirm("not-a-real-token")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LINK_EXPIRED"));
        confirm("")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LINK_EXPIRED"));
    }

    @Test
    void resendIssuesNewLinkAndInvalidatesPreviousOne() throws Exception {
        String first = mailSender.lastTokenFor(EMAIL).orElseThrow();

        resend(session).andExpect(status().isAccepted());
        awaitMailCount(mailSender, EMAIL, 2);
        String second = mailSender.lastTokenFor(EMAIL).orElseThrow();
        assertThat(second).isNotEqualTo(first);

        confirm(first)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LINK_EXPIRED"));
        confirm(second).andExpect(status().isOk());
    }

    @Test
    void resendIsLimitedToOncePerMinute() throws Exception {
        resend(session).andExpect(status().isAccepted());
        resend(session)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"))
                .andExpect(header().exists("Retry-After"));
        awaitMailCount(mailSender, EMAIL, 2);
        SignupRequests.assertMailCountStays(mailSender, EMAIL, 2);
    }

    @Test
    void resendIsLimitedToTenPerDay() throws Exception {
        String today =
                LocalDate.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.BASIC_ISO_DATE);
        String dailyKey = "auth:verify-resend:" + memberId + ":" + today;
        for (int i = 0; i < 10; i++) {
            redis.delete("rl:verify-resend:" + memberId); // 1분 제한은 이 테스트에서 건너뛴다
            resend(session).andExpect(status().isAccepted());
        }
        redis.delete("rl:verify-resend:" + memberId);
        resend(session)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
        assertThat(redis.getExpire(dailyKey)).isGreaterThan(Duration.ofDays(1).toSeconds());
    }

    @Test
    void sameTokenConcurrentlySucceedsOnlyOnce() throws Exception {
        String token = mailSender.lastTokenFor(EMAIL).orElseThrow();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(
                        pool.submit(
                                () -> {
                                    start.await();
                                    return confirm(token).andReturn().getResponse().getStatus();
                                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : futures) {
                statuses.add(f.get());
            }
            assertThat(statuses).containsExactlyInAnyOrder(200, 400);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void alreadyVerifiedResendIsConflict() throws Exception {
        confirm(mailSender.lastTokenFor(EMAIL).orElseThrow()).andExpect(status().isOk());
        resend(session)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_VERIFIED"));
    }

    @Test
    void resendRequiresLogin() throws Exception {
        resend(null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_REQUIRED"));
    }

    @Test
    void redisOutageRejectsConfirmAndResendWith503() throws Exception {
        String token = mailSender.lastTokenFor(EMAIL).orElseThrow();
        Cookie stableSession = TestLogin.loginAs(mockMvc, memberId);
        try (RedisOutage ignored = RedisOutage.start()) {
            confirm(token)
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("TEMPORARILY_UNAVAILABLE"))
                    .andExpect(header().string("Retry-After", "30"));
        }
        assertThat(verifiedAt()).isNull();
        // 재발송은 로그인이 필요한데 세션도 Redis에 있으므로, Redis가 멈추면 세션을 읽지 못해 비로그인(401)이 먼저 나온다(FR-040).
        // 세션은 읽혔지만 그 뒤에 Redis가 멈춘 경우의 503은 EmailVerificationService가 맡는다.
        try (RedisOutage ignored = RedisOutage.start()) {
            resend(stableSession)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("LOGIN_REQUIRED"));
        }
    }

    @Test
    void resendServiceRejectsWith503WhenRedisIsDownAfterSessionWasRead() {
        try (RedisOutage ignored = RedisOutage.start()) {
            org.assertj.core.api.Assertions.assertThatThrownBy(
                            () -> emailVerificationService.resend(memberId))
                    .isInstanceOf(TemporarilyUnavailableException.class);
        }
    }

    private ResultActions confirm(String token) throws Exception {
        return mockMvc.perform(
                TestLogin.withCsrf(
                        post("/api/auth/email-verification/confirm")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"token\":\"" + token + "\"}"),
                        null));
    }

    private ResultActions resend(Cookie cookie) throws Exception {
        return mockMvc.perform(TestLogin.withCsrf(post("/api/auth/email-verification"), cookie));
    }

    private Object verifiedAt() {
        return jdbc.queryForObject(
                "SELECT email_verified_at FROM auth_identity WHERE member_id = ?",
                Object.class,
                memberId);
    }
}
