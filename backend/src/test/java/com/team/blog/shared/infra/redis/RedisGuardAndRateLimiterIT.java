package com.team.blog.shared.infra.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team.blog.post.application.exception.AutosaveUnavailableException;
import com.team.blog.shared.infra.ratelimit.RateLimitResult;
import com.team.blog.shared.infra.ratelimit.RateLimiter;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.RedisOutage;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.redis.core.RedisCallback;

/**
 * 002가 {@link RedisGuard}에 더한 회로 차단기·OOM 처리와 002 요청 제한 키 (002 T017, research B-5, FR-018). 001 기본
 * 동작(장애 시 대체 경로·요청 제한 통과)은 {@code RateLimiterIntegrationTest}가 맡는다.
 */
@ExtendWith(OutputCaptureExtension.class)
class RedisGuardAndRateLimiterIT extends IntegrationTestBase {

    @Autowired RedisGuard redisGuard;
    @Autowired RateLimiter rateLimiter;
    @Autowired CircuitBreakerRegistry circuitBreakers;

    private CircuitBreaker breaker() {
        return circuitBreakers.circuitBreaker(RedisGuard.CIRCUIT_BREAKER);
    }

    @AfterEach
    void restore() {
        breaker().transitionToClosedState();
        setMaxMemory("0");
    }

    @Test
    void 회로_차단기_설정은_application_yml_값이다() {
        var config = breaker().getCircuitBreakerConfig();
        assertThat(config.getFailureRateThreshold()).isEqualTo(50f);
        assertThat(config.getSlidingWindowSize()).isEqualTo(20);
        assertThat(config.getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(5);
        assertThat(config.getWaitIntervalFunctionInOpenState().apply(1))
                .isEqualTo(Duration.ofSeconds(30).toMillis());
    }

    @Test
    void 회로가_열려_있으면_Redis를_부르지_않고_대체_경로를_쓴다() {
        breaker().transitionToForcedOpenState();
        AtomicBoolean called = new AtomicBoolean();

        String result =
                redisGuard.call(
                        () -> {
                            called.set(true);
                            return "redis";
                        },
                        () -> "fallback");

        assertThat(result).isEqualTo("fallback");
        assertThat(called).isFalse();
        assertThat(redisGuard.isAvailable()).isFalse();
        assertThat(rateLimiter.tryAcquire("ratelimit:autosave:1", 1, Duration.ofSeconds(5)))
                .isInstanceOf(RateLimitResult.Allowed.class);
    }

    @Test
    void 연결_실패와_시간_초과는_실패로_센다() {
        try (RedisOutage outage = RedisOutage.start()) {
            String result = redisGuard.call(() -> redis.opsForValue().get("k"), () -> "fallback");
            assertThat(result).isEqualTo("fallback");
        }
        assertThat(breaker().getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
        assertThat(redisGuard.call(() -> redis.opsForValue().get("k"), () -> "fallback")).isNull();
        assertThat(breaker().getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(1);
    }

    @Test
    void 실패가_절반을_넘으면_회로가_열린다() {
        for (int i = 0; i < 10; i++) {
            redisGuard.call(() -> "ok", () -> "fallback");
        }
        try (RedisOutage outage = RedisOutage.start()) {
            for (int i = 0; i < 10; i++) {
                redisGuard.call(() -> redis.opsForValue().get("k"), () -> "fallback");
            }
        }
        assertThat(breaker().getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(redisGuard.call(() -> "redis", () -> "fallback")).isEqualTo("fallback");
    }

    @Test
    void OOM은_실패로_세지_않고_AutosaveUnavailableException으로_바뀐다() {
        setMaxMemory("1");
        assertThatThrownBy(
                        () ->
                                redisGuard.call(
                                        () -> {
                                            redis.opsForHash().put("autosave:post:1", "title", "x");
                                            return "saved";
                                        },
                                        () -> "fallback"))
                .isInstanceOf(AutosaveUnavailableException.class)
                .satisfies(
                        e ->
                                assertThat(((AutosaveUnavailableException) e).reasonCode().code())
                                        .isEqualTo("AUTOSAVE_UNAVAILABLE"));
        assertThat(breaker().getMetrics().getNumberOfFailedCalls()).isZero();
        assertThat(breaker().getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void 자동_저장_요청_제한_5초에_1번() {
        String key = "ratelimit:autosave:7";
        assertThat(rateLimiter.tryAcquire(key, 1, Duration.ofSeconds(5)).allowed()).isTrue();
        RateLimitResult second = rateLimiter.tryAcquire(key, 1, Duration.ofSeconds(5));
        assertThat(second).isInstanceOf(RateLimitResult.Denied.class);
        assertThat(((RateLimitResult.Denied) second).retryAfterSeconds()).isBetween(1L, 5L);
    }

    @Test
    void 창이_지나면_다시_허용한다() throws Exception {
        String key = "ratelimit:autosave:8";
        assertThat(rateLimiter.tryAcquire(key, 1, Duration.ofMillis(600)).allowed()).isTrue();
        assertThat(rateLimiter.tryAcquire(key, 1, Duration.ofMillis(600)).allowed()).isFalse();
        Thread.sleep(800);
        assertThat(rateLimiter.tryAcquire(key, 1, Duration.ofMillis(600)).allowed()).isTrue();
    }

    @Test
    void 미리보기_요청_제한_1분에_60번() {
        String key = "ratelimit:preview:7";
        for (int i = 0; i < 60; i++) {
            assertThat(rateLimiter.tryAcquire(key, 60, Duration.ofMinutes(1)).allowed()).isTrue();
        }
        assertThat(rateLimiter.tryAcquire(key, 60, Duration.ofMinutes(1)).allowed()).isFalse();
    }

    @Test
    void Redis가_멈추면_002_키도_통과하고_경고를_남긴다(CapturedOutput output) {
        try (RedisOutage outage = RedisOutage.start()) {
            assertThat(rateLimiter.tryAcquire("ratelimit:autosave:9", 1, Duration.ofSeconds(5)))
                    .isInstanceOf(RateLimitResult.Allowed.class);
            assertThat(rateLimiter.tryAcquire("ratelimit:autosave:9", 1, Duration.ofSeconds(5)))
                    .isInstanceOf(RateLimitResult.Allowed.class);
        }
        assertThat(output.getOut() + output.getErr()).contains("요청 제한을 건너뜁니다");
    }

    private void setMaxMemory(String value) {
        redis.execute(
                (RedisCallback<Void>)
                        connection -> {
                            connection.serverCommands().setConfig("maxmemory", value);
                            return null;
                        });
    }
}
