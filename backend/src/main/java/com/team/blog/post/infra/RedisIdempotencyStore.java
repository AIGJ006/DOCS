package com.team.blog.post.infra;

import com.team.blog.shared.infra.redis.RedisGuard;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisStringCommands.SetOption;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 발행 멱등 키 보관소 (002 T109, research A-8·B-8, data-model §4). 키 {@code idem:publish:{memberId}:{key}},
 * 값 JSON {@code {hash, status: IN_PROGRESS | DONE, response}}, TTL {@code
 * blog.publish.idempotency-ttl}(기본 600초).
 *
 * <p>모든 호출은 {@link RedisGuard}를 거친다. Redis 장애면 {@link Start.Skipped}를 돌려주고(호출한 쪽은 멱등 처리 없이 발행하고, 행
 * 잠금 + 버전 확인이 중복 발행을 막는다), 완료·해제는 경고 로그 후 건너뛴다. 완료·해제는 DB 커밋 뒤에 부른다.
 */
@Component
public class RedisIdempotencyStore {

    private static final Logger log = LoggerFactory.getLogger(RedisIdempotencyStore.class);

    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String DONE = "DONE";

    private final StringRedisTemplate redis;
    private final RedisGuard redisGuard;
    private final JsonMapper json;

    public RedisIdempotencyStore(
            StringRedisTemplate redis, RedisGuard redisGuard, JsonMapper json) {
        this.redis = redis;
        this.redisGuard = redisGuard;
        this.json = json;
    }

    /** 저장된 값. {@code responseJson}은 {@code DONE}일 때의 응답 본문(JSON), 처리 중이면 {@code null}. */
    public record Entry(String hash, String status, String responseJson) {}

    /** {@link #tryStart} 결과. */
    public sealed interface Start {
        /** 새 키 — 이 요청이 처리한다. */
        record Started() implements Start {}

        /** 이미 있는 키. */
        record Existing(Entry entry) implements Start {}

        /** Redis 장애 — 멱등 처리를 건너뛴다. */
        record Skipped() implements Start {}
    }

    public static String key(long memberId, String idempotencyKey) {
        return "idem:publish:" + memberId + ":" + idempotencyKey;
    }

    /** {@code SET key {hash, IN_PROGRESS} NX PX ttl}. 이미 있으면 그 값. */
    public Start tryStart(long memberId, String idempotencyKey, String hash, Duration ttl) {
        String key = key(memberId, idempotencyKey);
        String value = write(new Entry(hash, IN_PROGRESS, null));
        return redisGuard.callWrite(
                () -> {
                    // 있던 키가 그 사이 지워졌으면(실패 해제·만료) 한 번 더 잡는다
                    for (int attempt = 0; attempt < 2; attempt++) {
                        if (Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, value, ttl))) {
                            return new Start.Started();
                        }
                        Optional<Entry> existing = read(redis.opsForValue().get(key));
                        if (existing.isPresent()) {
                            return new Start.Existing(existing.get());
                        }
                    }
                    return new Start.Started();
                },
                () -> {
                    log.warn("Redis 장애로 발행 멱등 키 처리를 건너뜁니다: memberId={}", memberId);
                    return new Start.Skipped();
                });
    }

    /** {@code SET key {hash, DONE, response} XX KEEPTTL} (남은 TTL 유지). */
    public void complete(long memberId, String idempotencyKey, String hash, String responseJson) {
        byte[] key = key(memberId, idempotencyKey).getBytes(StandardCharsets.UTF_8);
        byte[] value = write(new Entry(hash, DONE, responseJson)).getBytes(StandardCharsets.UTF_8);
        redisGuard.runWrite(
                () ->
                        redis.execute(
                                (RedisCallback<Boolean>)
                                        connection ->
                                                connection
                                                        .stringCommands()
                                                        .set(
                                                                key,
                                                                value,
                                                                Expiration.keepTtl(),
                                                                SetOption.ifPresent())),
                () -> log.warn("Redis 장애로 발행 멱등 응답 저장을 건너뜁니다: memberId={}", memberId));
    }

    /** 실패한 발행의 키를 지워 같은 키로 다시 시도할 수 있게 한다. */
    public void release(long memberId, String idempotencyKey) {
        redisGuard.runWrite(
                () -> redis.delete(key(memberId, idempotencyKey)),
                () -> log.warn("Redis 장애로 발행 멱등 키 해제를 건너뜁니다: memberId={}", memberId));
    }

    private String write(Entry entry) {
        ObjectNode node = json.createObjectNode();
        node.put("hash", entry.hash());
        node.put("status", entry.status());
        if (entry.responseJson() != null) {
            node.set("response", json.readTree(entry.responseJson()));
        } else {
            node.putNull("response");
        }
        return json.writeValueAsString(node);
    }

    private Optional<Entry> read(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        JsonNode node = json.readTree(raw);
        JsonNode response = node.get("response");
        return Optional.of(
                new Entry(
                        node.path("hash").asString(),
                        node.path("status").asString(),
                        response == null || response.isNull()
                                ? null
                                : json.writeValueAsString(response)));
    }
}
