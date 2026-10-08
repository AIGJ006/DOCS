package com.team.blog.account.infra.redis;

import com.team.blog.shared.infra.redis.RedisGuard;
import java.time.Duration;
import java.util.List;
import java.util.OptionalLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * 연속 실패 카운터 (R-12). 실패할 때마다 {@code INCR} + 잠금 기간만큼 {@code PEXPIRE}(마지막 실패부터 다시 셈) → 횟수가 한도에 닿으면 남은
 * TTL 동안 잠금. 성공하면 지운다. Redis 장애면 잠그지 않고 통과한다(02 §2-1 "실패 카운터 → 통과"). 쓰기는 트랜잭션 밖에서 부른다({@code
 * RedisGuard.callWrite}).
 */
abstract class RedisFailureCounter {

    private static final Logger log = LoggerFactory.getLogger(RedisFailureCounter.class);

    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> RECORD =
            new DefaultRedisScript<>(
                    """
                    local count = redis.call('INCR', KEYS[1])
                    redis.call('PEXPIRE', KEYS[1], ARGV[1])
                    return {count}
                    """,
                    List.class);

    private final StringRedisTemplate redis;
    private final RedisGuard redisGuard;
    private final int maxFailures;
    private final Duration lockDuration;

    RedisFailureCounter(
            StringRedisTemplate redis,
            RedisGuard redisGuard,
            int maxFailures,
            Duration lockDuration) {
        this.redis = redis;
        this.redisGuard = redisGuard;
        this.maxFailures = maxFailures;
        this.lockDuration = lockDuration;
    }

    /** 잠겨 있으면 남은 초(1 이상). */
    protected OptionalLong lockedForKey(String key) {
        return redisGuard.call(
                () -> {
                    String value = redis.opsForValue().get(key);
                    if (value == null || Long.parseLong(value) < maxFailures) {
                        return OptionalLong.empty();
                    }
                    Long ttl = redis.getExpire(key);
                    return OptionalLong.of(Math.max(1, ttl == null || ttl < 0 ? 1 : ttl));
                },
                () -> {
                    log.warn("Redis 장애로 실패 카운터 확인을 건너뜁니다");
                    return OptionalLong.empty();
                });
    }

    /** 실패 한 번. 이번 실패로 잠기면 남은 초. */
    @SuppressWarnings("unchecked")
    protected OptionalLong recordFailureForKey(String key) {
        return redisGuard.callWrite(
                () -> {
                    List<Long> result =
                            (List<Long>)
                                    redis.execute(
                                            RECORD,
                                            List.of(key),
                                            String.valueOf(lockDuration.toMillis()));
                    long count = result.getFirst();
                    return count >= maxFailures
                            ? OptionalLong.of(lockDuration.toSeconds())
                            : OptionalLong.empty();
                },
                () -> {
                    log.warn("Redis 장애로 실패 횟수를 세지 못했습니다");
                    return OptionalLong.empty();
                });
    }

    protected void resetKey(String key) {
        redisGuard.runWrite(() -> redis.delete(key), () -> {});
    }
}
