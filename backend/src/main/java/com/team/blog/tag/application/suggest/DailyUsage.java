package com.team.blog.tag.application.suggest;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * 회원 하루 AI 호출 수 (013 T017, contracts/providers.md §8, research R4). 키 {@code
 * ai:tag:usage:{memberId}:{yyyyMMdd 서비스 시간대}}, TTL 2일.
 *
 * <p>공급자를 부르기 전에 {@link #reserve}로 자리를 잡는다(INCR, 한도를 넘으면 그 자리에서 DECR 후 429) — 동시에 여러 요청이 와도 한도를 넘지
 * 않는다. AI가 답을 주지 않은 요청(시간 초과·형식 깨짐·혼잡)만 {@link #release}로 되돌린다(Clarifications Q4). 같은 요청의 자리 잡기와
 * 되돌리기는 같은 {@code now}를 넘겨 같은 날 키를 고친다.
 */
@Component
public class DailyUsage {

    static final String KEY_PREFIX = "ai:tag:usage:";
    static final Duration KEY_TTL = Duration.ofDays(2);

    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> RESERVE =
            new DefaultRedisScript<>(
                    """
                    local count = redis.call('INCR', KEYS[1])
                    if count == 1 then
                      redis.call('EXPIRE', KEYS[1], ARGV[2])
                    end
                    if count > tonumber(ARGV[1]) then
                      redis.call('DECR', KEYS[1])
                      return {0, count - 1}
                    end
                    return {1, count}
                    """,
                    List.class);

    private final StringRedisTemplate redis;
    private final AiRedis aiRedis;
    private final TagSuggestProperties properties;
    private final ZoneId zone;

    public DailyUsage(
            StringRedisTemplate redis,
            AiRedis aiRedis,
            TagSuggestProperties properties,
            ZoneId serviceZoneId) {
        this.redis = redis;
        this.aiRedis = aiRedis;
        this.properties = properties;
        this.zone = serviceZoneId;
    }

    /**
     * 자리 하나를 잡는다.
     *
     * @return 잡은 뒤 오늘 쓴 수
     * @throws AiDailyLimitException 오늘 한도를 다 씀
     * @throws AiUnavailableException Redis 장애 ({@code STORE_UNAVAILABLE})
     */
    @SuppressWarnings("unchecked")
    public int reserve(long memberId, Instant now) {
        String key = key(memberId, now);
        List<Long> result =
                aiRedis.call(
                        () ->
                                (List<Long>)
                                        redis.execute(
                                                RESERVE,
                                                List.of(key),
                                                String.valueOf(limit()),
                                                String.valueOf(KEY_TTL.toSeconds())));
        if (result.get(0) == 0L) {
            throw new AiDailyLimitException(now, resetAt(now));
        }
        return result.get(1).intValue();
    }

    /** 잡은 자리를 되돌린다. 실패해도 응답을 바꾸지 않는다. */
    public void release(long memberId, Instant now) {
        String key = key(memberId, now);
        aiRedis.quietly(() -> redis.opsForValue().decrement(key));
    }

    /** 오늘 남은 수 ({@code max(0, 한도 − 쓴 수)}). */
    public int remaining(long memberId, Instant now) {
        String key = key(memberId, now);
        String value = aiRedis.call(() -> redis.opsForValue().get(key));
        int used = value == null ? 0 : Math.max(0, Integer.parseInt(value));
        return Math.max(0, limit() - used);
    }

    /** 다음 서비스 시간대 0시. */
    public Instant resetAt(Instant now) {
        return LocalDate.ofInstant(now, zone).plusDays(1).atStartOfDay(zone).toInstant();
    }

    String key(long memberId, Instant now) {
        return KEY_PREFIX
                + memberId
                + ":"
                + LocalDate.ofInstant(now, zone).format(DateTimeFormatter.BASIC_ISO_DATE);
    }

    private int limit() {
        return properties.dailyLimitPerMember();
    }
}
