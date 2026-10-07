package com.team.blog.post.infra;

import com.team.blog.shared.infra.redis.RedisGuard;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Redis 자동 저장 보관소 (002 T033 1부: 읽기·정리). 모든 호출은 {@link RedisGuard}를 거친다 — Redis 장애면 읽기는 빈 결과, 정리는 경고
 * 로그 후 건너뜀(남은 키는 1분 반영의 버전 조건이 막고 24시간 TTL로 사라진다).
 *
 * <p>정리({@link #release}·{@link #delete})는 DB 커밋 <b>후</b>에 부른다. 트랜잭션 안에서 부르면 경고를 남긴다(05 J-5, T119에서
 * 강제).
 */
@Component
public class RedisAutosaveStore {

    private static final Logger log = LoggerFactory.getLogger(RedisAutosaveStore.class);

    private static final RedisScript<Long> RELEASE =
            new DefaultRedisScript<>(loadScript("redis/autosave-release.lua"), Long.class);

    private final StringRedisTemplate redis;
    private final RedisGuard redisGuard;

    public RedisAutosaveStore(StringRedisTemplate redis, RedisGuard redisGuard) {
        this.redis = redis;
        this.redisGuard = redisGuard;
    }

    /** {@code autosave:post:{postId}}. 없거나 Redis 장애면 empty. */
    public Optional<AutosaveEntry> find(long postId) {
        return redisGuard.call(
                () ->
                        toEntry(
                                redis.<String, String>opsForHash()
                                        .entries(AutosaveKeys.post(postId))),
                Optional::empty);
    }

    /**
     * 발행·변경 취소 커밋 후 정리 ({@code autosave-release.lua}). Redis 버전 ≤ {@code checkedVersion}이면 키를 지우고
     * dirty에서 뺀다. 더 크면(그 사이 다른 탭이 저장) 지우지 않고 버전을 {@code newDbVersion + 1}로 다시 매기며 dirty에 둔다.
     *
     * @param checkedVersion 발행·변경 취소 때 확인한 현재 버전 v0
     * @param newDbVersion 커밋한 새 {@code post.edit_version} v1
     */
    public void release(long postId, long checkedVersion, long newDbVersion) {
        warnIfInTransaction("release");
        redisGuard.run(
                () ->
                        redis.execute(
                                RELEASE,
                                List.of(AutosaveKeys.post(postId), AutosaveKeys.DIRTY),
                                String.valueOf(checkedVersion),
                                String.valueOf(newDbVersion),
                                String.valueOf(postId)),
                () -> log.warn("Redis 장애로 자동 저장 보관분 정리를 건너뜁니다: postId={}", postId));
    }

    /** 버전과 상관없이 지운다 (006 완전 삭제 커밋 후). */
    public void delete(long postId) {
        warnIfInTransaction("delete");
        redisGuard.run(
                () -> {
                    redis.delete(AutosaveKeys.post(postId));
                    redis.opsForSet().remove(AutosaveKeys.DIRTY, String.valueOf(postId));
                },
                () -> log.warn("Redis 장애로 자동 저장 보관분 삭제를 건너뜁니다: postId={}", postId));
    }

    private static Optional<AutosaveEntry> toEntry(Map<String, String> hash) {
        if (hash == null || hash.isEmpty() || hash.get(AutosaveKeys.FIELD_VERSION) == null) {
            return Optional.empty();
        }
        String savedAt = hash.get(AutosaveKeys.FIELD_SAVED_AT);
        return Optional.of(
                new AutosaveEntry(
                        Long.parseLong(hash.get(AutosaveKeys.FIELD_MEMBER_ID)),
                        hash.getOrDefault(AutosaveKeys.FIELD_TITLE, ""),
                        hash.getOrDefault(AutosaveKeys.FIELD_CONTENT_MD, ""),
                        Long.parseLong(hash.get(AutosaveKeys.FIELD_VERSION)),
                        savedAt == null ? null : Instant.parse(savedAt)));
    }

    private static void warnIfInTransaction(String operation) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            log.warn("자동 저장 보관분 {}를 트랜잭션 안에서 불렀습니다. 커밋 후에 불러야 합니다", operation);
        }
    }

    private static String loadScript(String path) {
        try {
            return new ClassPathResource(path)
                    .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Redis 스크립트를 읽지 못했습니다: " + path, e);
        }
    }
}
