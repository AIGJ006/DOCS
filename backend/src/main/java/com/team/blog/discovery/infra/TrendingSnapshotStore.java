package com.team.blog.discovery.infra;

import com.team.blog.shared.infra.redis.RedisGuard;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 트렌딩 스냅샷 보관소 (012 T026, research R4·R5, data-model §2). 키:
 *
 * <ul>
 *   <li>{@code trending:current} — 최신 스냅샷 ID({@code yyyyMMddHHmm} UTC), TTL 없음
 *   <li>{@code trending:{id}} — 글 번호 List(순위 순), TTL {@code snapshot-ttl}
 *   <li>{@code trending:{id}:count} — 글 수(0 가능 — 빈 List는 Redis에 없으므로 빈 순위와 만료를 구분), TTL 같음
 * </ul>
 *
 * 쓰기는 파이프라인 한 번({@code DEL → RPUSH → EXPIRE → SET count EX → SET current}). 모든 호출은 001 {@link
 * RedisGuard}로 감싸고, Redis 장애(연결 실패·시간 초과 등)면 {@link StoreUnavailableException}을 던져 부른 쪽이 대체 경로를 쓴다.
 */
@Component
public class TrendingSnapshotStore {

    public static final String CURRENT = "trending:current";

    private final StringRedisTemplate redis;
    private final RedisGuard redisGuard;

    public TrendingSnapshotStore(StringRedisTemplate redis, RedisGuard redisGuard) {
        this.redis = redis;
        this.redisGuard = redisGuard;
    }

    /** Redis를 쓸 수 없음 (대체 경로 신호). */
    public static class StoreUnavailableException extends RuntimeException {
        public StoreUnavailableException() {
            super("trending store unavailable", null, false, false);
        }
    }

    static String listKey(String snapshotId) {
        return "trending:" + snapshotId;
    }

    static String countKey(String snapshotId) {
        return "trending:" + snapshotId + ":count";
    }

    /** 스냅샷을 쓰고 {@code current}로 바꾼다. 트랜잭션 밖에서만 부른다. */
    public void write(String snapshotId, List<Long> ids, Duration ttl) {
        String list = listKey(snapshotId);
        String count = countKey(snapshotId);
        List<String> values = ids.stream().map(String::valueOf).toList();
        redisGuard.callWrite(
                () ->
                        redis.executePipelined(
                                new SessionCallback<Object>() {
                                    @Override
                                    @SuppressWarnings({"unchecked", "rawtypes"})
                                    public Object execute(RedisOperations operations)
                                            throws DataAccessException {
                                        operations.delete(list);
                                        if (!values.isEmpty()) {
                                            operations.opsForList().rightPushAll(list, values);
                                            operations.expire(list, ttl);
                                        }
                                        operations
                                                .opsForValue()
                                                .set(count, String.valueOf(values.size()), ttl);
                                        operations.opsForValue().set(CURRENT, snapshotId);
                                        return null;
                                    }
                                }),
                () -> {
                    throw new StoreUnavailableException();
                });
    }

    /** 최신 스냅샷 ID. 없으면 빈 값. */
    public Optional<String> current() {
        return Optional.ofNullable(read(() -> redis.opsForValue().get(CURRENT)));
    }

    /** 스냅샷의 글 수. 만료됐으면 빈 값. */
    public Optional<Integer> count(String snapshotId) {
        String value = read(() -> redis.opsForValue().get(countKey(snapshotId)));
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(value));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** {@code [start, end]} 위치의 글 번호 ({@code LRANGE}). */
    public List<Long> range(String snapshotId, long start, long end) {
        List<String> values = read(() -> redis.opsForList().range(listKey(snapshotId), start, end));
        if (values == null) {
            return List.of();
        }
        return values.stream().map(Long::valueOf).toList();
    }

    private <T> T read(java.util.function.Supplier<T> action) {
        return redisGuard.call(
                action,
                () -> {
                    throw new StoreUnavailableException();
                });
    }
}
