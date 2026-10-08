package com.team.blog.account.application.purge;

import com.team.blog.account.application.SessionTerminator;
import com.team.blog.account.application.WithdrawalProperties;
import com.team.blog.account.infra.redis.AuthTokenStore;
import com.team.blog.account.infra.redis.TokenType;
import com.team.blog.shared.infra.redis.RedisGuard;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 탈퇴 정리 커밋 뒤 Redis 정리 (015 T054, contracts/purge-steps.md §4, research R10). 세션, 인증·재설정 최신 토큰,
 * {@code blog.withdraw.purge.redis-key-templates}의 키를 지운다. 키 공간을 {@code SCAN}으로 훑지 않는다.
 *
 * <p>각 부분은 따로 시도하고 실패하면 WARN만 남긴다 — 정리 트랜잭션은 이미 커밋됐고, 남은 키는 모두 TTL이 있으며 익명 처리된 회원의 세션은 이미 비로그인
 * 취급이다. 로그에는 회원 번호만 남긴다.
 */
@Component
public class WithdrawalRedisCleaner {

    private static final Logger log = LoggerFactory.getLogger(WithdrawalRedisCleaner.class);

    static final String MEMBER_ID = "{memberId}";
    static final String EMAIL_HASH = "{emailHash}";

    private final SessionTerminator sessions;
    private final AuthTokenStore tokens;
    private final StringRedisTemplate redis;
    private final RedisGuard redisGuard;
    private final List<String> templates;

    public WithdrawalRedisCleaner(
            SessionTerminator sessions,
            AuthTokenStore tokens,
            StringRedisTemplate redis,
            RedisGuard redisGuard,
            WithdrawalProperties properties) {
        this.sessions = sessions;
        this.tokens = tokens;
        this.redis = redis;
        this.redisGuard = redisGuard;
        this.templates = properties.purge().redisKeyTemplates();
    }

    /**
     * @param memberId 정리한 회원
     * @param emailHash 로그인 이메일 SHA-256 hex. 없으면(이메일 없는 소셜 계정) {@code {emailHash}} 키는 건너뛴다
     */
    public void clean(long memberId, String emailHash) {
        try {
            sessions.terminateAll(memberId, Optional.empty());
        } catch (RuntimeException e) {
            log.warn(
                    "탈퇴 정리 Redis 세션 삭제 실패 memberId={}: {}", memberId, e.getClass().getSimpleName());
        }
        for (TokenType type : TokenType.values()) {
            try {
                tokens.revokeLatest(type, memberId);
            } catch (RuntimeException e) {
                log.warn(
                        "탈퇴 정리 Redis 토큰 삭제 실패 memberId={} type={}: {}",
                        memberId,
                        type,
                        e.getClass().getSimpleName());
            }
        }
        List<String> keys = keys(memberId, emailHash);
        if (keys.isEmpty()) {
            return;
        }
        try {
            redisGuard.runWrite(
                    () -> redis.delete(keys),
                    () -> log.warn("탈퇴 정리 Redis 키 삭제 실패 memberId={}", memberId));
        } catch (RuntimeException e) {
            log.warn("탈퇴 정리 Redis 키 삭제 실패 memberId={}: {}", memberId, e.getClass().getSimpleName());
        }
    }

    /** 템플릿을 채운 키 목록. {@code emailHash}가 없으면 그 키는 뺀다. */
    List<String> keys(long memberId, String emailHash) {
        List<String> keys = new ArrayList<>();
        for (String template : templates) {
            if (template.contains(EMAIL_HASH) && emailHash == null) {
                continue;
            }
            keys.add(
                    template.replace(MEMBER_ID, String.valueOf(memberId))
                            .replace(EMAIL_HASH, emailHash == null ? "" : emailHash));
        }
        return keys;
    }
}
