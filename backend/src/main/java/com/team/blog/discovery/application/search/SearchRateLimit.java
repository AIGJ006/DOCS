package com.team.blog.discovery.application.search;

import com.team.blog.interaction.application.VisitorKeyResolver;
import com.team.blog.shared.error.TooManyRequestsException;
import com.team.blog.shared.infra.ratelimit.RateLimiter;
import com.team.blog.shared.security.Viewer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 검색 요청 제한 (012 T036, research R12, FR-037). 글·사람 검색을 합쳐 같은 방문자 {@code blog.search.rate-limit}(1분
 * 30번), 키 {@code ratelimit:search:{방문자 키}}. 방문자 키는 009 {@link VisitorKeyResolver}({@code m:}/{@code
 * v:}/{@code h:} — 조회수와 같은 식별). 001 {@link RateLimiter}가 Redis 장애면 통과시키고, 방문자 키를 만들 수 없으면(하루 비밀값을 못
 * 얻음 — Redis 장애) 역시 통과다. 판정 순서의 맨 끝이라 검색 서비스가 다른 판정을 모두 마친 뒤 부른다.
 */
@Component
public class SearchRateLimit {

    public static final String KEY_PREFIX = "ratelimit:search:";

    private final VisitorKeyResolver visitorKeys;
    private final RateLimiter rateLimiter;
    private final SearchProperties properties;
    private final Clock clock;
    private final ZoneId zone;

    public SearchRateLimit(
            VisitorKeyResolver visitorKeys,
            RateLimiter rateLimiter,
            SearchProperties properties,
            Clock clock,
            ZoneId serviceZoneId) {
        this.visitorKeys = visitorKeys;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.clock = clock;
        this.zone = serviceZoneId;
    }

    /** 요청을 보낸 방문자. */
    public record Visitor(Viewer viewer, String vid, String clientIp, String userAgent) {}

    /**
     * @throws TooManyRequestsException 한도 초과 (429 {@code TOO_MANY_REQUESTS} + {@code Retry-After})
     */
    public void acquire(Visitor visitor) {
        Optional<String> key =
                visitorKeys.resolve(
                        visitor.viewer(),
                        visitor.vid(),
                        visitor.clientIp(),
                        visitor.userAgent(),
                        LocalDate.now(clock.withZone(zone)));
        if (key.isEmpty()) {
            return;
        }
        SearchProperties.RateLimit limit = properties.rateLimit();
        rateLimiter.acquireOrThrow(KEY_PREFIX + key.get(), limit.limit(), limit.window());
    }
}
