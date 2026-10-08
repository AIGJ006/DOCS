package com.team.blog.account.infra.redis;

import com.team.blog.account.infra.AccountProperties;
import com.team.blog.shared.error.TemporarilyUnavailableException;
import com.team.blog.shared.infra.redis.RedisGuard;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 메일 링크 토큰 (R-11, data-model §3, 07 §3·§4-1).
 *
 * <ul>
 *   <li>토큰: 32바이트 {@link SecureRandom} → Base64URL(패딩 없음, 43자).
 *   <li>{@code auth:verify:{token}} = memberId (TTL {@code blog.auth.verify.token-ttl}), {@code
 *       auth:reset:{token}} (TTL {@code blog.auth.reset.token-ttl}).
 *   <li>회원별 최신 포인터 {@code auth:verify-latest:{memberId}}: 새 토큰을 만들면 이전 토큰 키를 지운다 → 이전 링크
 *       무효(FR-006).
 *   <li>사용: {@code GETDEL}로 원자적으로 꺼내 지운다 → 같은 링크를 동시에 눌러도 한 번만 성공(SC-006). 최신 포인터와 같을 때만 회원 번호를
 *       돌려준다.
 *   <li>Redis 장애: {@link TemporarilyUnavailableException}(503) — 토큰 확인·발급은 보안상 통과시키지 않는다(FR-040).
 * </ul>
 *
 * 토큰 값은 로그에 남기지 않는다(FR-015).
 */
@Component
public class AuthTokenStore {

    private static final int TOKEN_BYTES = 32;
    private static final Pattern TOKEN_FORMAT = Pattern.compile("^[A-Za-z0-9_-]{43}$");

    private final StringRedisTemplate redis;
    private final RedisGuard redisGuard;
    private final AccountProperties.Auth settings;
    private final SecureRandom random = new SecureRandom();

    public AuthTokenStore(
            StringRedisTemplate redis, RedisGuard redisGuard, AccountProperties properties) {
        this.redis = redis;
        this.redisGuard = redisGuard;
        this.settings = properties.auth();
    }

    /** 새 토큰을 만들고 이전 토큰을 무효로 한다. */
    public String issue(TokenType type, long memberId) {
        String token = newToken();
        Duration ttl = ttl(type);
        String memberValue = String.valueOf(memberId);
        redisGuard.call(
                () -> {
                    String latestKey = type.latestKey(memberId);
                    String previous = redis.opsForValue().get(latestKey);
                    if (previous != null) {
                        redis.delete(type.tokenKey(previous));
                    }
                    redis.opsForValue().set(type.tokenKey(token), memberValue, ttl);
                    redis.opsForValue().set(latestKey, token, ttl);
                    return null;
                },
                AuthTokenStore::unavailable);
        return token;
    }

    /** 토큰을 한 번 쓴다. 없거나·만료·이전 링크면 빈 값. */
    public Optional<Long> consume(TokenType type, String token) {
        if (token == null || !TOKEN_FORMAT.matcher(token).matches()) {
            return Optional.empty();
        }
        return redisGuard.call(
                () -> {
                    String memberValue = redis.opsForValue().getAndDelete(type.tokenKey(token));
                    if (memberValue == null) {
                        return Optional.<Long>empty();
                    }
                    long memberId = Long.parseLong(memberValue);
                    String latestKey = type.latestKey(memberId);
                    if (!Objects.equals(redis.opsForValue().get(latestKey), token)) {
                        return Optional.<Long>empty();
                    }
                    redis.delete(latestKey);
                    return Optional.of(memberId);
                },
                AuthTokenStore::unavailable);
    }

    /** 토큰을 쓰지 않고 회원 번호만 본다(비밀번호 재설정: 새 비밀번호 규칙을 먼저 검사하고 실패하면 토큰을 남기기 위해). 최신 포인터와 다르면 빈 값. */
    public Optional<Long> peek(TokenType type, String token) {
        if (token == null || !TOKEN_FORMAT.matcher(token).matches()) {
            return Optional.empty();
        }
        return redisGuard.call(
                () -> {
                    String memberValue = redis.opsForValue().get(type.tokenKey(token));
                    if (memberValue == null) {
                        return Optional.<Long>empty();
                    }
                    long memberId = Long.parseLong(memberValue);
                    if (!Objects.equals(redis.opsForValue().get(type.latestKey(memberId)), token)) {
                        return Optional.<Long>empty();
                    }
                    return Optional.of(memberId);
                },
                AuthTokenStore::unavailable);
    }

    private Duration ttl(TokenType type) {
        return switch (type) {
            case VERIFY -> settings.verify().tokenTtl();
            case RESET -> settings.reset().tokenTtl();
        };
    }

    private String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static <T> T unavailable() {
        throw new TemporarilyUnavailableException();
    }
}
