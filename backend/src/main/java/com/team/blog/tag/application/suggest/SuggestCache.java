package com.team.blog.tag.application.suggest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * 재사용 저장소 (013 T041, contracts/providers.md §6, research R8).
 *
 * <ul>
 *   <li>같은 내용 {@code ai:tag:v{prompt-version}:{SHA-256(정리된 입력 전체)}} 30일 — 모든 사용자 공유
 *   <li>같은 글 비슷한 내용 {@code ai:tag:post:{postId}} 7일 — 지난 입력 앞 8,000자와 3-gram Jaccard ≥ 0.9
 * </ul>
 *
 * 저장 값은 정규화만 한 목록이다(이미 붙인 태그를 빼기 전) — 응답 때마다 결과 검사를 다시 한다(FR-026). [다시 추천]은 비슷한 내용을 건너뛰고, 같은 내용 결과가
 * 자체 AI 것이고 지금 외부 AI를 쓸 수 있으면 새로 만든다(FR-028). 빈 목록은 저장하지 않는다(FR-029).
 */
@Component
public class SuggestCache {

    static final String POST_PREFIX = "ai:tag:post:";

    /** 맞은 항목. */
    public record Hit(List<String> tags, Provider provider) {}

    private final StringRedisTemplate redis;
    private final AiRedis aiRedis;
    private final JsonMapper json;
    private final TagSuggestProperties properties;

    public SuggestCache(
            StringRedisTemplate redis,
            AiRedis aiRedis,
            JsonMapper json,
            TagSuggestProperties properties) {
        this.redis = redis;
        this.aiRedis = aiRedis;
        this.json = json;
        this.properties = properties;
    }

    /**
     * @param geminiAvailableNow 지금 라우터가 Gemini를 고르는가 ([다시 추천] 때만 묻는다)
     */
    public Optional<Hit> lookup(
            long postId, CleanedInput input, boolean refresh, BooleanSupplier geminiAvailableNow) {
        String exactKey = exactKey(input);
        CachedSuggestion exact = read(exactKey, CachedSuggestion.class);
        if (exact != null
                && !(refresh
                        && exact.provider() == Provider.OLLAMA
                        && geminiAvailableNow.getAsBoolean())) {
            return Optional.of(new Hit(exact.tags(), exact.provider()));
        }
        if (refresh) {
            return Optional.empty();
        }
        PostCachedSuggestion near = read(POST_PREFIX + postId, PostCachedSuggestion.class);
        if (near != null) {
            String now = input.truncate(properties.cache().postInputChars()).text();
            if (TrigramSimilarity.jaccard(now, near.input())
                    >= properties.cache().similarityThreshold()) {
                return Optional.of(new Hit(near.tags(), near.provider()));
            }
        }
        return Optional.empty();
    }

    /** 두 곳에 저장한다. 빈 목록이면 하지 않는다. 저장 실패는 응답을 바꾸지 않는다. */
    public void store(
            long postId, CleanedInput input, List<String> tags, Provider provider, Instant now) {
        if (tags.isEmpty()) {
            return;
        }
        String exact = json.writeValueAsString(new CachedSuggestion(tags, provider, now));
        String near =
                json.writeValueAsString(
                        new PostCachedSuggestion(
                                input.truncate(properties.cache().postInputChars()).text(),
                                tags,
                                provider,
                                now));
        String exactKey = exactKey(input);
        aiRedis.quietly(
                () -> {
                    redis.opsForValue().set(exactKey, exact, properties.cache().exactTtl());
                    redis.opsForValue()
                            .set(POST_PREFIX + postId, near, properties.cache().postTtl());
                });
    }

    String exactKey(CleanedInput input) {
        return "ai:tag:v" + properties.promptVersion() + ":" + input.sha256();
    }

    private <T> T read(String key, Class<T> type) {
        String value = aiRedis.call(() -> redis.opsForValue().get(key));
        if (value == null) {
            return null;
        }
        try {
            return json.readValue(value, type);
        } catch (JacksonException e) {
            return null;
        }
    }
}
