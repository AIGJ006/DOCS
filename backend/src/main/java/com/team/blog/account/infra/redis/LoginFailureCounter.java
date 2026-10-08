package com.team.blog.account.infra.redis;

import com.team.blog.account.application.EmailAddress;
import com.team.blog.account.infra.AccountProperties;
import com.team.blog.shared.infra.redis.RedisGuard;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.OptionalLong;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 이메일 로그인 연속 실패 (FR-035, R-12): {@code auth:login-fail:{sha256(정규화 이메일)}}, {@code
 * blog.auth.login.max-failures}(5)번 → {@code lock-duration}(15분) 잠금. 가입하지 않은 이메일도 똑같이 센다(가입 여부를
 * 드러내지 않음, SC-004). 성공하면 지운다. Redis 장애면 통과한다. 키에 이메일 원문을 두지 않는다.
 */
@Component
public class LoginFailureCounter extends RedisFailureCounter {

    static final String KEY = "auth:login-fail:";

    public LoginFailureCounter(
            StringRedisTemplate redis, RedisGuard redisGuard, AccountProperties properties) {
        super(
                redis,
                redisGuard,
                properties.auth().login().maxFailures(),
                properties.auth().login().lockDuration());
    }

    /** 잠겨 있으면 남은 초. */
    public OptionalLong lockedFor(String email) {
        return lockedForKey(key(email));
    }

    public OptionalLong recordFailure(String email) {
        return recordFailureForKey(key(email));
    }

    public void reset(String email) {
        resetKey(key(email));
    }

    static String key(String email) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(EmailAddress.normalize(email).getBytes(StandardCharsets.UTF_8));
            return KEY + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
