package com.team.blog.account.infra.redis;

import com.team.blog.account.infra.AccountProperties;
import com.team.blog.shared.infra.redis.RedisGuard;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 최근 활동 갱신 간격 (R-26, FR-059): {@code SET member:active-touch:{memberId} 1 NX EX
 * blog.member.last-active.touch-interval}(1시간). 키를 새로 만들었을 때만 true — 그때만 {@code last_active_at}을
 * 갱신한다. Redis 장애면 false(갱신 건너뜀, 02 §2-1). 트랜잭션 밖에서 부른다.
 */
@Component
public class ActiveTouchThrottle {

    static final String KEY = "member:active-touch:";

    private final StringRedisTemplate redis;
    private final RedisGuard redisGuard;
    private final Duration interval;

    public ActiveTouchThrottle(
            StringRedisTemplate redis, RedisGuard redisGuard, AccountProperties properties) {
        this.redis = redis;
        this.redisGuard = redisGuard;
        this.interval = properties.member().lastActive().touchInterval();
    }

    public boolean tryAcquire(long memberId) {
        return redisGuard.callWrite(
                () ->
                        Boolean.TRUE.equals(
                                redis.opsForValue().setIfAbsent(KEY + memberId, "1", interval)),
                () -> false);
    }
}
