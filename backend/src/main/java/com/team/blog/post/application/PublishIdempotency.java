package com.team.blog.post.application;

import com.team.blog.post.application.exception.IdempotencyKeyReusedException;
import com.team.blog.post.application.exception.PublishInProgressException;
import com.team.blog.post.config.PostAuthoringProperties;
import com.team.blog.post.domain.PublishResult;
import com.team.blog.post.infra.RedisIdempotencyStore;
import com.team.blog.post.infra.RedisIdempotencyStore.Start;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 발행 멱등 판정 (002 T110, FR-037, research A-8·B-8). 요청 지문은 {@code SHA-256(postId + 정렬된 키의 JSON(title,
 * contentMd, tags, visibility, baseVersion))}이라 JSON 키 순서·공백이 달라도 같은 요청으로 본다.
 *
 * <ul>
 *   <li>새 키 → {@link Decision.Proceed} (호출한 쪽이 발행한 뒤 {@link #complete} 또는 실패 시 {@link #release})
 *   <li>같은 키·다른 지문 → 422 {@code IDEMPOTENCY_KEY_REUSED}
 *   <li>같은 키·같은 지문·처리 중 → 409 {@code IN_PROGRESS}
 *   <li>같은 키·같은 지문·끝남 → {@link Decision.Replay} (저장된 응답, 이벤트 없음)
 *   <li>Redis 장애 → {@link Decision.Proceed} (멱등 처리 없이 진행, 행 잠금 + 버전 확인이 중복을 막는다)
 * </ul>
 */
@Component
public class PublishIdempotency {

    private final RedisIdempotencyStore store;
    private final JsonMapper json;
    private final PostAuthoringProperties properties;
    private final PostAuthoringMetrics metrics;

    public PublishIdempotency(
            RedisIdempotencyStore store,
            JsonMapper json,
            PostAuthoringProperties properties,
            PostAuthoringMetrics metrics) {
        this.store = store;
        this.metrics = metrics;
        this.json = json;
        this.properties = properties;
    }

    /** {@link #begin} 결과. */
    public sealed interface Decision {
        /** 이 요청이 발행한다. {@code hash}는 완료 때 다시 쓴다. */
        record Proceed(String hash) implements Decision {}

        /** 이미 끝난 같은 요청 — 저장된 응답을 그대로 돌려준다. */
        record Replay(PublishResult result) implements Decision {}
    }

    public Decision begin(
            long memberId,
            String idempotencyKey,
            long postId,
            String title,
            String contentMd,
            List<String> tags,
            String visibility,
            long baseVersion) {
        String hash = fingerprint(postId, title, contentMd, tags, visibility, baseVersion);
        Start start =
                store.tryStart(
                        memberId, idempotencyKey, hash, properties.publish().idempotencyTtl());
        return switch (start) {
            case Start.Started s -> new Decision.Proceed(hash);
            case Start.Skipped s -> {
                metrics.idempotencySkipped();
                yield new Decision.Proceed(hash);
            }
            case Start.Existing existing -> {
                RedisIdempotencyStore.Entry entry = existing.entry();
                if (!hash.equals(entry.hash())) {
                    throw new IdempotencyKeyReusedException();
                }
                if (!RedisIdempotencyStore.DONE.equals(entry.status())
                        || entry.responseJson() == null) {
                    throw new PublishInProgressException();
                }
                yield new Decision.Replay(readResponse(entry.responseJson()));
            }
        };
    }

    /** 발행이 커밋된 뒤 응답을 저장한다 (남은 TTL 유지). */
    public void complete(long memberId, String idempotencyKey, String hash, PublishResult result) {
        store.complete(memberId, idempotencyKey, hash, writeResponse(result));
    }

    /** 발행이 실패하면 키를 풀어 같은 키로 다시 시도할 수 있게 한다. */
    public void release(long memberId, String idempotencyKey) {
        store.release(memberId, idempotencyKey);
    }

    /** 요청 지문. 칸 값은 검증·정리 전의 요청 그대로다. */
    String fingerprint(
            long postId,
            String title,
            String contentMd,
            List<String> tags,
            String visibility,
            long baseVersion) {
        Map<String, Object> body = new TreeMap<>();
        body.put("baseVersion", baseVersion);
        body.put("contentMd", contentMd);
        body.put("tags", tags == null ? null : new ArrayList<>(tags));
        body.put("title", title);
        body.put("visibility", visibility);
        String canonical = postId + json.writeValueAsString(body);
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** contracts {@code PublishResponse}와 같은 모양으로 저장한다. */
    private String writeResponse(PublishResult r) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("url", r.url());
        body.put("publishedAt", text(r.publishedAt()));
        body.put("firstPublicAt", text(r.firstPublicAt()));
        body.put("editedAt", text(r.editedAt()));
        body.put("version", r.version());
        return json.writeValueAsString(body);
    }

    private PublishResult readResponse(String raw) {
        JsonNode node = json.readTree(raw);
        return new PublishResult(
                textOrNull(node, "url"),
                instant(node, "publishedAt"),
                instant(node, "firstPublicAt"),
                instant(node, "editedAt"),
                node.path("version").asLong(),
                false,
                false);
    }

    private static String text(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }

    private static Instant instant(JsonNode node, String field) {
        String value = textOrNull(node, field);
        return value == null ? null : Instant.parse(value);
    }
}
