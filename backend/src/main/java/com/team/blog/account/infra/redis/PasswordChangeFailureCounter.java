package com.team.blog.account.infra.redis;

import com.team.blog.account.infra.AccountProperties;
import com.team.blog.shared.infra.redis.RedisGuard;
import java.util.OptionalLong;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 비밀번호 변경의 현재 비밀번호 연속 실패 (FR-045, R-12): {@code auth:pw-change-fail:{memberId}}, {@code
 * blog.auth.password-change.max-failures}(5)번 → {@code lock-duration}(15분) 잠금.
 */
@Component
public class PasswordChangeFailureCounter extends RedisFailureCounter {

    static final String KEY = "auth:pw-change-fail:";

    public PasswordChangeFailureCounter(
            StringRedisTemplate redis, RedisGuard redisGuard, AccountProperties properties) {
        super(
                redis,
                redisGuard,
                properties.auth().passwordChange().maxFailures(),
                properties.auth().passwordChange().lockDuration());
    }

    public OptionalLong lockedFor(long memberId) {
        return lockedForKey(KEY + memberId);
    }

    public OptionalLong recordFailure(long memberId) {
        return recordFailureForKey(KEY + memberId);
    }

    public void reset(long memberId) {
        resetKey(KEY + memberId);
    }
}
