package com.team.blog.discovery.support;

import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 트렌딩 스냅샷 키를 직접 다루는 시험 도구 (012 T008, data-model §2). 키 이름은 {@code TrendingSnapshotStore}와 같다:
 * {@code trending:current}, {@code trending:{id}}(List), {@code trending:{id}:count}.
 */
public final class TrendingRedisHelper {

    public static final String CURRENT = "trending:current";

    private final StringRedisTemplate redis;

    public TrendingRedisHelper(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public static String listKey(String snapshotId) {
        return "trending:" + snapshotId;
    }

    public static String countKey(String snapshotId) {
        return "trending:" + snapshotId + ":count";
    }

    /** 스냅샷을 직접 쓰고 {@code current}로 둔다 (TTL 30분). */
    public void write(String snapshotId, List<Long> ids) {
        redis.delete(listKey(snapshotId));
        if (!ids.isEmpty()) {
            redis.opsForList()
                    .rightPushAll(listKey(snapshotId), ids.stream().map(String::valueOf).toList());
            redis.expire(listKey(snapshotId), Duration.ofMinutes(30));
        }
        redis.opsForValue()
                .set(countKey(snapshotId), String.valueOf(ids.size()), Duration.ofMinutes(30));
        redis.opsForValue().set(CURRENT, snapshotId);
    }

    /** 스냅샷이 만료된 것처럼 지운다 (TTL이 지난 뒤와 같은 상태). */
    public void expire(String snapshotId) {
        redis.delete(List.of(listKey(snapshotId), countKey(snapshotId)));
    }

    public String current() {
        return redis.opsForValue().get(CURRENT);
    }

    public List<Long> ids(String snapshotId) {
        List<String> values = redis.opsForList().range(listKey(snapshotId), 0, -1);
        return values == null ? List.of() : values.stream().map(Long::valueOf).toList();
    }

    public String count(String snapshotId) {
        return redis.opsForValue().get(countKey(snapshotId));
    }

    /** 남은 TTL(초). 키가 없으면 -2, TTL이 없으면 -1. */
    public long ttlSeconds(String key) {
        Long ttl = redis.getExpire(key);
        return ttl == null ? -2 : ttl;
    }

    public void clearCurrent() {
        redis.delete(CURRENT);
    }
}
