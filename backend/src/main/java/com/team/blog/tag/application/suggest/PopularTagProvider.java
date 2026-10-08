package com.team.blog.tag.application.suggest;

import com.team.blog.post.application.exception.AutosaveUnavailableException;
import com.team.blog.shared.infra.redis.RedisGuard;
import com.team.blog.tag.application.TagCountView;
import com.team.blog.tag.application.TagQueryService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 프롬프트 ②의 인기 태그 (013 T014, contracts/providers.md §2). {@code ai:tag:popular:{yyyyMMdd 서비스 시간대}}에
 * 하루 보관하고, 없으면 008 {@link TagQueryService#top()} 앞 {@code popular.size}개 이름을 넣는다.
 *
 * <p>있으면 좋은 값이라 Redis 장애·메모리 부족이면 저장 없이 DB 값을 쓴다(추천을 막지 않는다).
 */
@Component
public class PopularTagProvider {

    static final String KEY_PREFIX = "ai:tag:popular:";
    private static final TypeReference<List<String>> NAMES = new TypeReference<>() {};

    private final TagQueryService tags;
    private final StringRedisTemplate redis;
    private final RedisGuard guard;
    private final JsonMapper json;
    private final TagSuggestProperties properties;
    private final ZoneId zone;

    public PopularTagProvider(
            TagQueryService tags,
            StringRedisTemplate redis,
            RedisGuard guard,
            JsonMapper json,
            TagSuggestProperties properties,
            ZoneId serviceZoneId) {
        this.tags = tags;
        this.redis = redis;
        this.guard = guard;
        this.json = json;
        this.properties = properties;
        this.zone = serviceZoneId;
    }

    public List<String> today(Instant now) {
        int size = properties.popular().size();
        if (size == 0) {
            return List.of();
        }
        String key =
                KEY_PREFIX
                        + LocalDate.ofInstant(now, zone).format(DateTimeFormatter.BASIC_ISO_DATE);
        String cached;
        try {
            cached = guard.call(() -> redis.opsForValue().get(key), () -> null);
        } catch (AutosaveUnavailableException e) {
            cached = null;
        }
        if (cached != null) {
            try {
                return json.readValue(cached, NAMES);
            } catch (JacksonException e) {
                // 깨진 값은 다시 만든다
            }
        }
        List<String> names =
                tags.top().items().stream().map(TagCountView::name).limit(size).toList();
        String value = json.writeValueAsString(names);
        try {
            guard.runWrite(
                    () -> redis.opsForValue().set(key, value, properties.popular().ttl()),
                    () -> {});
        } catch (AutosaveUnavailableException e) {
            // 저장하지 못해도 이번 목록은 쓴다
        }
        return names;
    }
}
