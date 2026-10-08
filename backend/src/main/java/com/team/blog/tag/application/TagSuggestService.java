package com.team.blog.tag.application;

import com.team.blog.shared.infra.ratelimit.RateLimiter;
import com.team.blog.tag.domain.TagNormalizer;
import com.team.blog.tag.infra.TagQueryRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 태그 자동완성 (008 T044, US3, FR-033, research R11).
 *
 * <p>판정 순서: 401(컨트롤러 {@code @LoginRequired}) → 검색어 정리(금칙어 검사 없음, 비거나 형식이 틀리면 SQL·제한 없이 {@code []})
 * → 요청 제한(회원당 {@code blog.tag.suggest.rate-limit}, 넘으면 429 {@code TOO_MANY_REQUESTS}, 판정 순서 맨 끝 —
 * 007 Q2) → SQL 한 번. Redis 장애면 001 {@link RateLimiter}가 통과시킨다.
 *
 * <p>트랜잭션을 열지 않는다 — Redis 카운트가 DB 트랜잭션에 묶이지 않게 한다(SQL은 한 문장이라 따로 묶을 것이 없다).
 */
@Service
public class TagSuggestService {

    static final String RATE_LIMIT_KEY_PREFIX = "ratelimit:tag-suggest:";

    private final TagNormalizer normalizer;
    private final TagQueryRepository repository;
    private final RateLimiter rateLimiter;
    private final TagProperties properties;

    public TagSuggestService(
            TagNormalizer normalizer,
            TagQueryRepository repository,
            RateLimiter rateLimiter,
            TagProperties properties) {
        this.normalizer = normalizer;
        this.repository = repository;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
    }

    public List<TagSuggestionView> suggest(String q, long memberId) {
        Optional<String> prefix = normalizer.normalizeQuery(q == null ? "" : q);
        if (prefix.isEmpty()) {
            return List.of();
        }
        TagProperties.Suggest suggest = properties.suggest();
        rateLimiter.acquireOrThrow(
                RATE_LIMIT_KEY_PREFIX + memberId,
                suggest.rateLimit().limit(),
                suggest.rateLimit().window());
        return repository.suggest(prefix.get(), memberId, suggest.limit()).stream()
                .map(row -> new TagSuggestionView(row.name(), row.postCount(), row.mine()))
                .toList();
    }
}
