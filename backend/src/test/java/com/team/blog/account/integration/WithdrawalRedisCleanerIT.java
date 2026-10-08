package com.team.blog.account.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team.blog.account.application.purge.WithdrawalPurgeRunner;
import com.team.blog.account.application.purge.WithdrawalPurgeRunner.Outcome;
import com.team.blog.account.application.purge.WithdrawalPurgeRunner.Reason;
import com.team.blog.account.application.purge.WithdrawalPurgeStepException;
import com.team.blog.account.infra.redis.AuthTokenStore;
import com.team.blog.account.infra.redis.TokenType;
import com.team.blog.account.support.WithdrawalPurgeProbe;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.RedisOutage;
import com.team.blog.support.TestLogin;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;

/** 정리 커밋 뒤 Redis 정리 (015 T045, contracts/purge-steps.md §4, research R10). */
@ExtendWith(OutputCaptureExtension.class)
class WithdrawalRedisCleanerIT extends IntegrationTestBase {

    private static final String EMAIL = "redis-leaver@example.com";

    @Autowired WithdrawalPurgeRunner runner;
    @Autowired WithdrawalPurgeProbe probe;
    @Autowired AuthTokenStore tokens;
    @Autowired FindByIndexNameSessionRepository<? extends Session> sessions;

    private long me;
    private long other;
    private Cookie session;
    private String verifyToken;
    private String resetToken;

    @BeforeEach
    void setUp() throws Exception {
        probe.reset();
        me = members().member().email(EMAIL).create();
        other = members().member().create();
        session = TestLogin.loginAs(mockMvc, me);
        verifyToken = tokens.issue(TokenType.VERIFY, me);
        resetToken = tokens.issue(TokenType.RESET, me);
        for (String key : memberKeys()) {
            redis.opsForValue().set(key, "1", Duration.ofMinutes(10));
        }
        redis.opsForValue().set("ratelimit:like:" + other, "1", Duration.ofMinutes(10));
        jdbc.update(
                "UPDATE member SET status = 'WITHDRAWN', withdrawn_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minus(Duration.ofDays(31))),
                me);
    }

    private List<String> memberKeys() {
        return List.of(
                "auth:pw-change-fail:" + me,
                "auth:login-fail:" + sha256(EMAIL),
                "rl:reset:email:" + sha256(EMAIL),
                "rl:verify-resend:" + me,
                "ratelimit:like:" + me,
                "ratelimit:comment:" + me,
                "member:active-touch:" + me);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private boolean exists(String key) {
        return Boolean.TRUE.equals(redis.hasKey(key));
    }

    @Test
    void 커밋_뒤_세션_토큰_횟수_키가_사라진다() {
        assertThat(runner.purgeOne(me, Reason.GRACE_EXPIRED)).isEqualTo(Outcome.PURGED);

        assertThat(sessions.findById(TestLogin.sessionId(session))).isNull();
        assertThat(exists("auth:verify-latest:" + me)).isFalse();
        assertThat(exists("auth:reset-latest:" + me)).isFalse();
        assertThat(exists("auth:verify:" + verifyToken)).isFalse();
        assertThat(exists("auth:reset:" + resetToken)).isFalse();
        for (String key : memberKeys()) {
            assertThat(exists(key)).as(key).isFalse();
        }
        assertThat(exists("ratelimit:like:" + other)).isTrue();
    }

    @Test
    void 롤백되면_키가_그대로() {
        probe.failFor(me);

        assertThatThrownBy(() -> runner.purgeOne(me, Reason.GRACE_EXPIRED))
                .isInstanceOf(WithdrawalPurgeStepException.class);

        assertThat(sessions.findById(TestLogin.sessionId(session))).isNotNull();
        assertThat(exists("auth:verify:" + verifyToken)).isTrue();
        assertThat(exists("auth:reset-latest:" + me)).isTrue();
        for (String key : memberKeys()) {
            assertThat(exists(key)).as(key).isTrue();
        }
    }

    @Test
    void Redis_정지_중에도_정리는_커밋되고_WARN만(CapturedOutput output) {
        try (RedisOutage outage = RedisOutage.start()) {
            assertThat(runner.purgeOne(me, Reason.GRACE_EXPIRED)).isEqualTo(Outcome.PURGED);
        }
        assertThat(
                        jdbc.queryForObject(
                                "SELECT deleted_at FROM member WHERE id = ?", Timestamp.class, me))
                .isNotNull();
        assertThat(output.getAll()).contains("탈퇴 정리 Redis").doesNotContain(EMAIL);
    }
}
