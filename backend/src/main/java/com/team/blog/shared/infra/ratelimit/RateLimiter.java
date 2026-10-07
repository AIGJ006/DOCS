package com.team.blog.shared.infra.ratelimit;

import com.team.blog.shared.error.TooManyRequestsException;
import com.team.blog.shared.infra.redis.RedisGuard;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * 고정 창 요청 제한. 키마다 창(window) 동안 {@code limit}번까지 허용한다. Lua 스크립트 하나로 {@code INCR} + 첫 증가 때 {@code
 * PEXPIRE}를 원자적으로 한다.
 *
 * <p>Redis 장애 시에는 예외 없이 <b>허용</b>한다(경고 로그) — 02 §2-1 "요청 제한·중복 방지 카운터 → 통과". 키 이름 규칙은 각 기능이 정한다(예:
 * {@code rl:login:ip:{ip}}, {@code ratelimit:autosave:{memberId}}). "같은 IP" 키는 {@code
 * shared.web.ClientIp}를 쓴다.
 */
@Component
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> SCRIPT =
            new DefaultRedisScript<>(
                    """
                    local count = redis.call('INCR', KEYS[1])
                    local ttl = redis.call('PTTL', KEYS[1])
                    if count == 1 or ttl < 0 then
                      redis.call('PEXPIRE', KEYS[1], ARGV[1])
                      ttl = tonumber(ARGV[1])
                    end
                    return {count, ttl}
                    """,
                    List.class);

    private static final RateLimitResult ALLOWED = new RateLimitResult.Allowed();

    private final StringRedisTemplate redis;
    private final RedisGuard redisGuard;

    public RateLimiter(StringRedisTemplate redis, RedisGuard redisGuard) {
        this.redis = redis;
        this.redisGuard = redisGuard;
    }

    /**
     * 한 번 센다. 한도를 넘으면 {@link RateLimitResult.Denied}. Redis 장애면 {@link RateLimitResult.Allowed}.
     */
    public RateLimitResult tryAcquire(String key, int limit, Duration window) {
        if (limit < 1 || window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("limit·window는 양수여야 합니다");
        }
        return redisGuard.call(
                () -> count(key, limit, window),
                () -> {
                    log.warn("Redis 장애로 요청 제한을 건너뜁니다: key={}", keyPrefix(key));
                    return ALLOWED;
                });
    }

    /** 한 번 센다. 한도를 넘으면 429 {@link TooManyRequestsException}. */
    public void acquireOrThrow(String key, int limit, Duration window) {
        if (tryAcquire(key, limit, window) instanceof RateLimitResult.Denied denied) {
            throw new TooManyRequestsException(denied.retryAfterSeconds());
        }
    }

    @SuppressWarnings("unchecked")
    private RateLimitResult count(String key, int limit, Duration window) {
        List<Long> result =
                (List<Long>) redis.execute(SCRIPT, List.of(key), String.valueOf(window.toMillis()));
        long count = result.get(0);
        long ttlMillis = result.get(1);
        if (count <= limit) {
            return ALLOWED;
        }
        long seconds = Math.max(1, (ttlMillis + 999) / 1000);
        return new RateLimitResult.Denied(seconds);
    }

    /** 로그에는 키의 앞부분(종류)만 남긴다 — IP·이메일 해시는 남기지 않는다. */
    private static String keyPrefix(String key) {
        int idx = key.lastIndexOf(':');
        return idx > 0 ? key.substring(0, idx) : key;
    }
}
