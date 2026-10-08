package com.team.blog.tag.application.suggest;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 공급자 상태 키 (013 T047·T048, contracts/providers.md §5, data-model §2).
 *
 * <ul>
 *   <li>{@code ai:gemini:count:{qd}} 우리가 센 오늘 외부 호출 수 (TTL 2일), {@code qd} = {@code
 *       gemini.quota-zone} 날짜
 *   <li>{@code ai:gemini:exhausted} 하루 한도 소진 (TTL = 그 시간대 다음 0시까지)
 *   <li>{@code ai:gemini:cooldown} 분당 한도·시간 초과·서버 오류·모르는 429 뒤 쉬는 시간 (기본 60초)
 *   <li>{@code ai:gemini:unknown-429:{qd}} 종류 모르는 429 수 (3번이면 소진)
 *   <li>{@code ai:ollama:inflight} 지금 처리 중인 자체 AI 요청 수 (매 INCR 때 TTL 60초 — 되돌리지 못한 경우의 안전장치)
 * </ul>
 *
 * 판단에 쓰는 읽기·자리 잡기는 장애면 503 {@code STORE_UNAVAILABLE}, 호출 뒤 상태 기록·되돌리기는 장애여도 응답을 바꾸지 않는다.
 */
@Component
public class ProviderState {

    static final String COUNT = "ai:gemini:count:";
    static final String EXHAUSTED = "ai:gemini:exhausted";
    static final String COOLDOWN = "ai:gemini:cooldown";
    static final String UNKNOWN_429 = "ai:gemini:unknown-429:";
    static final String OLLAMA_INFLIGHT = "ai:ollama:inflight";
    static final Duration DAY_KEY_TTL = Duration.ofDays(2);
    static final Duration INFLIGHT_TTL = Duration.ofSeconds(60);

    private final StringRedisTemplate redis;
    private final AiRedis aiRedis;
    private final TagSuggestProperties properties;

    public ProviderState(
            StringRedisTemplate redis, AiRedis aiRedis, TagSuggestProperties properties) {
        this.redis = redis;
        this.aiRedis = aiRedis;
        this.properties = properties;
    }

    /** 지금 Gemini를 쉬어야 하는가. 우리 집계가 하루 한도에 닿았으면 다음 초기화까지 소진으로 적는다. */
    public boolean geminiBlocked(Instant now) {
        String countKey = COUNT + quotaDate(now);
        List<String> values =
                aiRedis.call(
                        () -> redis.opsForValue().multiGet(List.of(EXHAUSTED, COOLDOWN, countKey)));
        if (values == null) {
            return false;
        }
        if (values.get(0) != null || values.get(1) != null) {
            return true;
        }
        String count = values.get(2);
        if (count != null && Long.parseLong(count) >= properties.gemini().dailyLimit()) {
            exhaust(now);
            return true;
        }
        return false;
    }

    /** 외부 호출 하나를 센다 (호출 전). */
    public void countGeminiCall(Instant now) {
        String key = COUNT + quotaDate(now);
        aiRedis.quietly(
                () -> {
                    Long n = redis.opsForValue().increment(key);
                    if (n != null && n == 1L) {
                        redis.expire(key, DAY_KEY_TTL);
                    }
                });
    }

    /** 성공 — 모르는 429 수를 지운다. */
    public void geminiSucceeded(Instant now) {
        aiRedis.quietly(() -> redis.delete(UNKNOWN_429 + quotaDate(now)));
    }

    /** 하루 한도 소진 — 공급자 시간대 다음 0시까지. */
    public void exhaust(Instant now) {
        Duration ttl = untilNextReset(now);
        aiRedis.quietly(() -> redis.opsForValue().set(EXHAUSTED, "1", ttl));
    }

    /** 잠깐 쉬기 (기본 60초). */
    public void cooldown() {
        Duration ttl = properties.gemini().cooldown();
        aiRedis.quietly(() -> redis.opsForValue().set(COOLDOWN, "1", ttl));
    }

    /** 종류 모르는 429 — 쉬고, 오늘 {@code unknown-429-exhaust-count}번째면 소진. */
    public void unknown429(Instant now) {
        cooldown();
        String key = UNKNOWN_429 + quotaDate(now);
        int limit = properties.gemini().unknown429ExhaustCount();
        aiRedis.quietly(
                () -> {
                    Long n = redis.opsForValue().increment(key);
                    if (n != null && n == 1L) {
                        redis.expire(key, DAY_KEY_TTL);
                    }
                    if (n != null && n >= limit) {
                        redis.opsForValue().set(EXHAUSTED, "1", untilNextReset(now));
                    }
                });
    }

    /** 자체 AI 자리를 잡는다. 넘치면 되돌리고 {@code false}. */
    public boolean acquireOllama() {
        int max = properties.ollama().maxConcurrency();
        Long n =
                aiRedis.call(
                        () -> {
                            Long v = redis.opsForValue().increment(OLLAMA_INFLIGHT);
                            redis.expire(OLLAMA_INFLIGHT, INFLIGHT_TTL);
                            return v;
                        });
        if (n != null && n > max) {
            releaseOllama();
            return false;
        }
        return true;
    }

    public void releaseOllama() {
        aiRedis.quietly(() -> redis.opsForValue().decrement(OLLAMA_INFLIGHT));
    }

    /** 공급자 시간대 날짜 {@code yyyyMMdd}. */
    String quotaDate(Instant now) {
        return LocalDate.ofInstant(now, zone()).format(DateTimeFormatter.BASIC_ISO_DATE);
    }

    /** 공급자 시간대 다음 0시까지 (최소 1초). */
    Duration untilNextReset(Instant now) {
        ZoneId zone = zone();
        Instant next = LocalDate.ofInstant(now, zone).plusDays(1).atStartOfDay(zone).toInstant();
        Duration d = Duration.between(now, next);
        return d.compareTo(Duration.ofSeconds(1)) < 0 ? Duration.ofSeconds(1) : d;
    }

    private ZoneId zone() {
        return properties.gemini().quotaZone();
    }
}
