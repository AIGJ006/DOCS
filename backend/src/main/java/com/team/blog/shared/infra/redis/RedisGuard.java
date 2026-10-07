package com.team.blog.shared.infra.redis;

import com.team.blog.post.application.exception.AutosaveUnavailableException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis 장애 판정과 대체 경로 (02 §2-1: 글 읽기는 계속, 보안상 필요한 것만 거부).
 *
 * <p>{@link #call(Supplier, Supplier)}는 Redis 연결 실패·응답 시간 초과({@code
 * spring.data.redis.timeout})·Redis 오류를 잡아 대체 값을 돌려준다. 대체 값에서 예외를 던지면 거부(예: 토큰 확인 → 503)가 된다.
 *
 * <p><b>회로 차단기 (002 research B-5)</b>: 모든 호출을 Resilience4j {@code CircuitBreaker("redis")}로 감싼다(설정
 * {@code resilience4j.circuitbreaker.instances.redis}: 최근 20회 중 50% 실패 → 30초 열림 → 반열림 5회). 열려 있으면
 * Redis를 부르지 않고 바로 대체 경로를 쓴다(장애 동안 매 요청 시간 초과를 기다리지 않음). 실패로 세는 것은 연결 실패·시간 초과뿐이다. 그 밖의 Redis 오류는
 * 대체 경로를 쓰되 세지 않는다.
 *
 * <p><b>메모리 부족({@code OOM})</b>: 장애가 아니라 쓰기 거부다. 세지 않고 대체 경로도 쓰지 않으며 {@link
 * AutosaveUnavailableException}(503)을 던진다 — 자동 저장을 DB로 우회하지 않고 브라우저가 재시도한다(FR-018, 밀어내지 않음). 002
 * 문서의 {@code execute(call, fallback)}·{@code isOpen()}은 {@link #call}·{@code !}{@link
 * #isAvailable()}이다.
 */
@Component
public class RedisGuard {

    private static final Logger log = LoggerFactory.getLogger(RedisGuard.class);

    /** 회로 차단기 이름 ({@code resilience4j.circuitbreaker.instances.redis}). */
    public static final String CIRCUIT_BREAKER = "redis";

    private final StringRedisTemplate redis;
    private final CircuitBreaker breaker;

    public RedisGuard(StringRedisTemplate redis, CircuitBreakerRegistry circuitBreakers) {
        this.redis = redis;
        this.breaker = circuitBreakers.circuitBreaker(CIRCUIT_BREAKER);
    }

    /**
     * Redis 호출. 회로가 열려 있거나 장애면 경고 로그 후 {@code fallback} 결과.
     *
     * @throws AutosaveUnavailableException Redis 메모리 부족({@code OOM})
     */
    public <T> T call(Supplier<T> action, Supplier<T> fallback) {
        if (!breaker.tryAcquirePermission()) {
            log.warn("Redis 회로가 열려 있어 대체 경로를 씁니다");
            return fallback.get();
        }
        long start = System.nanoTime();
        try {
            T result = action.get();
            breaker.onSuccess(System.nanoTime() - start, TimeUnit.NANOSECONDS);
            return result;
        } catch (RuntimeException e) {
            if (isOutOfMemory(e)) {
                breaker.releasePermission();
                log.warn("Redis 메모리 부족으로 쓰기를 받지 못했습니다");
                throw new AutosaveUnavailableException(e);
            }
            if (!isRedisFailure(e)) {
                breaker.releasePermission();
                throw e;
            }
            if (isConnectionFailureOrTimeout(e)) {
                breaker.onError(System.nanoTime() - start, TimeUnit.NANOSECONDS, e);
            } else {
                breaker.releasePermission();
            }
            log.warn("Redis 장애로 대체 경로를 씁니다: {}", e.getClass().getSimpleName());
            return fallback.get();
        }
    }

    /** 결과가 없는 Redis 호출. 장애면 경고 로그 후 {@code fallback} 실행. */
    public void run(Runnable action, Runnable fallback) {
        call(
                () -> {
                    action.run();
                    return null;
                },
                () -> {
                    fallback.run();
                    return null;
                });
    }

    /** 지금 Redis가 응답하는가: 회로가 열려 있지 않고 PING이 PONG. */
    public boolean isAvailable() {
        CircuitBreaker.State state = breaker.getState();
        if (state == CircuitBreaker.State.OPEN || state == CircuitBreaker.State.FORCED_OPEN) {
            return false;
        }
        try {
            String pong = redis.execute((RedisCallback<String>) connection -> connection.ping());
            return "PONG".equalsIgnoreCase(pong);
        } catch (RuntimeException e) {
            if (isRedisFailure(e)) {
                return false;
            }
            throw e;
        }
    }

    /** 회로 차단기에 실패로 세는 예외인가 (연결 실패·시간 초과만). */
    static boolean isConnectionFailureOrTimeout(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof RedisConnectionFailureException
                    || t instanceof QueryTimeoutException) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }

    /** Redis가 {@code maxmemory}를 넘어 쓰기를 거부했는가 ({@code OOM command not allowed …}). */
    public static boolean isOutOfMemory(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String message = t.getMessage();
            if (message != null && message.strip().toUpperCase(Locale.ROOT).startsWith("OOM ")) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }

    /** Redis 장애로 볼 예외인가 (연결 실패·시간 초과·Redis 시스템 오류). */
    public static boolean isRedisFailure(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof RedisConnectionFailureException
                    || t instanceof QueryTimeoutException
                    || t instanceof RedisSystemException) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }
}
