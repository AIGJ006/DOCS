package com.team.blog.post.infra;

import com.team.blog.post.domain.ServerCopy;
import com.team.blog.shared.infra.redis.RedisGuard;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> SAVE =
            new DefaultRedisScript<>(loadScript("redis/autosave-save.lua"), List.class);

    private static final RedisScript<Long> CLEAR_DIRTY =
            new DefaultRedisScript<>(loadScript("redis/autosave-clear-dirty.lua"), Long.class);

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

    /**
     * 자동 저장 Lua 결과 (T076).
     *
     * <ul>
     *   <li>{@link Accepted}: 받아들임, 새 버전
     *   <li>{@link Rejected}: 기준 버전 ≠ 현재 버전. 현재 내용이 Redis에 있으면 {@code redisCopy}
     *   <li>{@link NotOwner}: 키의 회원이 다름
     *   <li>{@link Unavailable}: Redis 장애(회로 열림·연결 실패) — 호출한 쪽이 DB 경로를 쓴다
     * </ul>
     */
    public sealed interface SaveOutcome {
        record Accepted(long version) implements SaveOutcome {}

        record Rejected(long currentVersion, Optional<ServerCopy> redisCopy)
                implements SaveOutcome {}

        record NotOwner() implements SaveOutcome {}

        record Unavailable() implements SaveOutcome {}
    }

    /**
     * 버전 확인 + 저장 ({@code autosave-save.lua}, 원자적). 현재 = max(Redis {@code version}, {@code
     * dbVersion}), {@code baseVersion}이 같으면 Hash 5필드 + TTL + dirty 등록.
     *
     * @throws com.team.blog.post.application.exception.AutosaveUnavailableException Redis 메모리
     *     부족(503, DB로 우회하지 않음)
     */
    @SuppressWarnings("unchecked")
    public SaveOutcome save(
            long postId,
            long memberId,
            long baseVersion,
            long dbVersion,
            String title,
            String contentMd,
            Instant savedAt,
            Duration ttl) {
        return redisGuard.call(
                () -> {
                    List<Object> reply =
                            redis.execute(
                                    SAVE,
                                    List.of(AutosaveKeys.post(postId), AutosaveKeys.DIRTY),
                                    String.valueOf(memberId),
                                    String.valueOf(baseVersion),
                                    String.valueOf(dbVersion),
                                    title,
                                    contentMd,
                                    savedAt.toString(),
                                    String.valueOf(ttl.toMillis()),
                                    String.valueOf(postId));
                    return toOutcome(reply);
                },
                SaveOutcome.Unavailable::new);
    }

    private static SaveOutcome toOutcome(List<Object> reply) {
        String flag = String.valueOf(reply.get(0));
        return switch (flag) {
            case "1" -> new SaveOutcome.Accepted(Long.parseLong(String.valueOf(reply.get(1))));
            case "-1" -> new SaveOutcome.NotOwner();
            default -> {
                long current = Long.parseLong(String.valueOf(reply.get(1)));
                Optional<ServerCopy> copy =
                        reply.size() >= 5
                                ? Optional.of(
                                        new ServerCopy(
                                                String.valueOf(reply.get(2)),
                                                String.valueOf(reply.get(3)),
                                                current,
                                                parseInstant(String.valueOf(reply.get(4)))))
                                : Optional.empty();
                yield new SaveOutcome.Rejected(current, copy);
            }
        };
    }

    private static Instant parseInstant(String value) {
        return value == null || value.isEmpty() ? null : Instant.parse(value);
    }

    /** 1분 반영 대상 글 번호 ({@code SMEMBERS autosave:dirty}). Redis 장애면 빈 집합. */
    public Set<Long> dirtyPostIds() {
        return redisGuard.call(
                () -> {
                    Set<String> members = redis.opsForSet().members(AutosaveKeys.DIRTY);
                    Set<Long> ids = new LinkedHashSet<>();
                    if (members != null) {
                        for (String member : members) {
                            try {
                                ids.add(Long.parseLong(member));
                            } catch (NumberFormatException e) {
                                log.warn("autosave:dirty에 숫자가 아닌 값이 있습니다: {}", member);
                            }
                        }
                    }
                    return ids;
                },
                Set::of);
    }

    /** 1분 반영 뒤: 키 버전이 반영한 버전과 같을 때만(또는 키가 없으면) dirty에서 뺀다. */
    public void clearDirtyIfVersion(long postId, long flushedVersion) {
        redisGuard.run(
                () ->
                        redis.execute(
                                CLEAR_DIRTY,
                                List.of(AutosaveKeys.post(postId), AutosaveKeys.DIRTY),
                                String.valueOf(flushedVersion),
                                String.valueOf(postId)),
                () -> log.warn("Redis 장애로 dirty 정리를 건너뜁니다: postId={}", postId));
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
