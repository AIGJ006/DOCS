package com.team.blog.shared.infra.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.shared.infra.redis.RedisGuard;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.RedisOutage;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** 고정 창 요청 제한 + Redis 장애 시 통과 (02 §2-1 "요청 제한·중복 방지 카운터 → 통과"). */
@ExtendWith(OutputCaptureExtension.class)
class RateLimiterIntegrationTest extends IntegrationTestBase {

    @Autowired RateLimiter rateLimiter;

    @Autowired RedisGuard redisGuard;

    @Test
    void 한도를_넘으면_거부하고_남은_초를_알려준다() {
        String key = "rl:test:203.0.113.7";
        for (int i = 0; i < 3; i++) {
            assertThat(rateLimiter.tryAcquire(key, 3, Duration.ofSeconds(60)))
                    .isInstanceOf(RateLimitResult.Allowed.class);
        }
        RateLimitResult fourth = rateLimiter.tryAcquire(key, 3, Duration.ofSeconds(60));
        assertThat(fourth).isInstanceOf(RateLimitResult.Denied.class);
        long retryAfter = ((RateLimitResult.Denied) fourth).retryAfterSeconds();
        assertThat(retryAfter).isBetween(1L, 60L);
        assertThat(redis.getExpire(key)).isBetween(1L, 60L);
    }

    @Test
    void 창이_지나면_다시_허용한다() throws Exception {
        String key = "rl:test:window";
        assertThat(rateLimiter.tryAcquire(key, 1, Duration.ofMillis(700)).allowed()).isTrue();
        assertThat(rateLimiter.tryAcquire(key, 1, Duration.ofMillis(700)).allowed()).isFalse();
        Thread.sleep(900);
        assertThat(rateLimiter.tryAcquire(key, 1, Duration.ofMillis(700)).allowed()).isTrue();
    }

    @Test
    void 키마다_따로_센다() {
        assertThat(rateLimiter.tryAcquire("rl:test:a", 1, Duration.ofSeconds(60)).allowed())
                .isTrue();
        assertThat(rateLimiter.tryAcquire("rl:test:b", 1, Duration.ofSeconds(60)).allowed())
                .isTrue();
        assertThat(rateLimiter.tryAcquire("rl:test:a", 1, Duration.ofSeconds(60)).allowed())
                .isFalse();
    }

    @Test
    void Redis가_멈추면_예외_없이_허용하고_경고를_남긴다(CapturedOutput output) {
        String key = "rl:test:outage";
        assertThat(rateLimiter.tryAcquire(key, 1, Duration.ofSeconds(60)).allowed()).isTrue();
        try (RedisOutage outage = RedisOutage.start()) {
            assertThat(redisGuard.isAvailable()).isFalse();
            for (int i = 0; i < 3; i++) {
                assertThat(rateLimiter.tryAcquire(key, 1, Duration.ofSeconds(60)))
                        .isInstanceOf(RateLimitResult.Allowed.class);
            }
        }
        assertThat(output.getOut() + output.getErr()).contains("요청 제한을 건너뜁니다");
        assertThat(redisGuard.isAvailable()).isTrue();
        assertThat(rateLimiter.tryAcquire(key, 1, Duration.ofSeconds(60)).allowed()).isFalse();
    }

    @Test
    void acquireOrThrow는_한도를_넘으면_429_예외() {
        String key = "rl:test:throw";
        rateLimiter.acquireOrThrow(key, 1, Duration.ofSeconds(60));
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> rateLimiter.acquireOrThrow(key, 1, Duration.ofSeconds(60)))
                .isInstanceOf(com.team.blog.shared.error.TooManyRequestsException.class);
    }
}
