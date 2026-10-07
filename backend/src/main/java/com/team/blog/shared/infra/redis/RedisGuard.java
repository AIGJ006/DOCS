package com.team.blog.shared.infra.redis;

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
 * spring.data.redis.timeout})·Redis 오류를 잡아 대체 값을 돌려준다. 대체 값에서 예외를 던지면 거부(예: 토큰 확인 → 503)가 된다. 002는
 * 이 클래스에 회로 차단기를 더한다.
 */
@Component
public class RedisGuard {

    private static final Logger log = LoggerFactory.getLogger(RedisGuard.class);

    private final StringRedisTemplate redis;

    public RedisGuard(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Redis 호출. 장애면 경고 로그 후 {@code fallback} 결과. */
    public <T> T call(Supplier<T> action, Supplier<T> fallback) {
        try {
            return action.get();
        } catch (RuntimeException e) {
            if (!isRedisFailure(e)) {
                throw e;
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

    /** 지금 Redis가 응답하는가 (PING). */
    public boolean isAvailable() {
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
