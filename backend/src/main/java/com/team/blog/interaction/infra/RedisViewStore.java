package com.team.blog.interaction.infra;

import com.team.blog.interaction.application.ViewSaltSource;
import com.team.blog.shared.infra.redis.RedisGuard;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * 조회수 Redis 저장소 (009 T031, data-model §2, research R5·R6·R8). 모든 호출은 {@link RedisGuard}를 거치고 트랜잭션
 * 밖에서만 부른다(쓰기는 {@code callWrite}).
 *
 * <table>
 *   <caption>키</caption>
 *   <tr><td>{@code view:seen:{postId}:{visitorKey}}</td><td>기간 안 조회 횟수, TTL = 기간(첫 조회부터)</td></tr>
 *   <tr><td>{@code view:pending:{yyyyMMdd}}</td><td>Hash postId → 모은 조회 수, TTL 48시간(안전망)</td></tr>
 *   <tr><td>{@code view:processing:{yyyyMMdd}:{uuid}}</td><td>반영 중 묶음, TTL 없음</td></tr>
 *   <tr><td>{@code view:salt:{yyyyMMdd}}</td><td>하루 비밀값(32바이트 난수 Base64), TTL 26시간</td></tr>
 * </table>
 *
 * 기록 쪽({@link #record}·{@link #salt})은 장애면 건너뛴다. 반영 쪽({@link #scan}·{@link #claim}·{@link
 * #entries}·{@link #remove})은 장애면 {@link ViewStoreUnavailableException}을 던져 그 회차를 끝낸다 — 처리 중 묶음은 남아
 * 다음 회차가 이어 간다.
 */
@Component
public class RedisViewStore implements ViewSaltSource {

    /** 기록 결과. */
    public enum RecordOutcome {
        COUNTED,
        DUPLICATE,
        /** Redis 장애·회로 열림으로 건너뜀. */
        SKIPPED
    }

    public static final String PENDING_PREFIX = "view:pending:";
    public static final String PROCESSING_PREFIX = "view:processing:";
    private static final String SEEN_PREFIX = "view:seen:";
    private static final String SALT_PREFIX = "view:salt:";
    private static final Duration SALT_TTL = Duration.ofHours(26);
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    private static final RedisScript<Long> RECORD =
            new DefaultRedisScript<>(load("redis/view-record.lua"), Long.class);
    private static final RedisScript<Long> CLAIM =
            new DefaultRedisScript<>(load("redis/view-claim.lua"), Long.class);

    private final StringRedisTemplate redis;
    private final RedisGuard redisGuard;
    private final SecureRandom random = new SecureRandom();

    public RedisViewStore(StringRedisTemplate redis, RedisGuard redisGuard) {
        this.redis = redis;
        this.redisGuard = redisGuard;
    }

    public static String dateText(LocalDate date) {
        return date.format(DAY);
    }

    public static String pendingKey(LocalDate date) {
        return PENDING_PREFIX + dateText(date);
    }

    /** 묶음 키({@code view:pending:{d}}·{@code view:processing:{d}:{uuid}})의 날짜. 형식이 틀리면 빈 값. */
    public static Optional<LocalDate> dateOf(String key) {
        String rest;
        if (key.startsWith(PENDING_PREFIX)) {
            rest = key.substring(PENDING_PREFIX.length());
        } else if (key.startsWith(PROCESSING_PREFIX)) {
            rest = key.substring(PROCESSING_PREFIX.length());
            int colon = rest.indexOf(':');
            rest = colon < 0 ? rest : rest.substring(0, colon);
        } else {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(rest, DAY));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    /**
     * 중복 판정 + 모음 (Lua 한 번, 원자적). 기간은 첫 조회부터, 동시 요청도 {@code INCR}로 {@code maxPerWindow}번까지만 센다.
     *
     * @param date 조회가 일어난 날짜(서비스 시간대) — 모음 키의 날짜
     */
    public RecordOutcome record(
            long postId, String visitorKey, LocalDate date, Duration window, int maxPerWindow) {
        long seconds = Math.max(1, window.toSeconds());
        return redisGuard.callWrite(
                () -> {
                    Long counted =
                            redis.execute(
                                    RECORD,
                                    List.of(
                                            SEEN_PREFIX + postId + ":" + visitorKey,
                                            pendingKey(date)),
                                    String.valueOf(seconds),
                                    String.valueOf(maxPerWindow),
                                    String.valueOf(postId));
                    return counted != null && counted == 1L
                            ? RecordOutcome.COUNTED
                            : RecordOutcome.DUPLICATE;
                },
                () -> RecordOutcome.SKIPPED);
    }

    /** 그날 비밀값 ({@code SET NX} + TTL 26시간 뒤 읽기). 장애면 빈 값. */
    @Override
    public Optional<String> salt(LocalDate date) {
        String key = SALT_PREFIX + dateText(date);
        return redisGuard.callWrite(
                () -> {
                    byte[] bytes = new byte[32];
                    random.nextBytes(bytes);
                    redis.opsForValue()
                            .setIfAbsent(key, Base64.getEncoder().encodeToString(bytes), SALT_TTL);
                    return Optional.ofNullable(redis.opsForValue().get(key));
                },
                Optional::empty);
    }

    /** 그 형식의 키 목록 ({@code SCAN}, 반영 쪽). */
    public List<String> scan(String pattern) {
        return redisGuard.call(
                () -> {
                    List<String> keys = new ArrayList<>();
                    try (Cursor<String> cursor =
                            redis.scan(
                                    ScanOptions.scanOptions().match(pattern).count(500).build())) {
                        cursor.forEachRemaining(keys::add);
                    }
                    return keys;
                },
                RedisViewStore::unavailable);
    }

    /**
     * 모음 묶음을 처리 중 묶음으로 옮긴다 ({@code RENAME} + {@code PERSIST}, Lua로 원자적).
     *
     * @return 처리 중 키. 그새 사라졌으면 빈 값
     */
    public Optional<String> claim(String pendingKey) {
        Optional<LocalDate> date = dateOf(pendingKey);
        if (date.isEmpty() || !pendingKey.startsWith(PENDING_PREFIX)) {
            return Optional.empty();
        }
        String processing = PROCESSING_PREFIX + dateText(date.get()) + ":" + UUID.randomUUID();
        Long moved =
                redisGuard.callWrite(
                        () -> redis.execute(CLAIM, List.of(pendingKey, processing)),
                        RedisViewStore::unavailable);
        return moved != null && moved == 1L ? Optional.of(processing) : Optional.empty();
    }

    /** 묶음의 (글 번호 → 조회 수) ({@code HSCAN}). 숫자가 아닌 항목은 0으로 둔다(반영 때 지워진다). */
    public Map<Long, Long> entries(String key) {
        return redisGuard.call(
                () -> {
                    Map<Long, Long> out = new LinkedHashMap<>();
                    try (Cursor<Map.Entry<Object, Object>> cursor =
                            redis.opsForHash()
                                    .scan(key, ScanOptions.scanOptions().count(500).build())) {
                        cursor.forEachRemaining(
                                e -> {
                                    Long postId = parse(e.getKey());
                                    if (postId != null) {
                                        Long n = parse(e.getValue());
                                        out.put(postId, n == null ? 0L : n);
                                    }
                                });
                    }
                    return out;
                },
                RedisViewStore::unavailable);
    }

    /** 반영을 마친 글 하나를 묶음에서 지운다 ({@code HDEL}, 커밋 뒤). 묶음이 비면 Redis가 키를 지운다. */
    public void remove(String key, long postId) {
        redisGuard.callWrite(
                () -> redis.opsForHash().delete(key, String.valueOf(postId)),
                RedisViewStore::unavailable);
    }

    private static Long parse(Object value) {
        try {
            return value == null ? null : Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static <T> T unavailable() {
        throw new ViewStoreUnavailableException();
    }

    private static String load(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Redis 스크립트를 읽지 못했습니다: " + path, e);
        }
    }
}
