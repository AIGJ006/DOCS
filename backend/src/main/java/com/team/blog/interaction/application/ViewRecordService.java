package com.team.blog.interaction.application;

import com.team.blog.interaction.infra.RedisViewStore;
import com.team.blog.interaction.infra.RedisViewStore.RecordOutcome;
import com.team.blog.post.application.PostReadService;
import com.team.blog.post.application.exception.AutosaveUnavailableException;
import com.team.blog.post.domain.PostNotFoundException;
import com.team.blog.post.domain.PostStatus;
import com.team.blog.post.infra.PostView;
import com.team.blog.shared.infra.ratelimit.RateLimiter;
import com.team.blog.shared.security.Viewer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

/**
 * 조회 기록 (009 T032, {@code POST /api/posts/{postId}/views}, contracts/view-pipeline.md §1, research
 * R4~R7).
 *
 * <ol>
 *   <li>① 글 확인 — 004 {@link PostReadService#requireReadable} + {@code PUBLISHED}. 볼 수 없으면
 *       404(봇·관리자여도 먼저 — 존재를 드러내지 않는다)
 *   <li>② 제외 — 작성자 본인·관리자·봇·미리 불러오기({@link ViewExclusion}). 기록 없이 같은 204
 *   <li>③ 방문자 키 — {@link VisitorKeyResolver}. 쿠키 없는 비회원의 비밀값을 얻지 못하면(Redis 장애) 건너뜀
 *   <li>④ 요청 제한 {@code ratelimit:view:{방문자 키}} 1분 60번 → 429. Redis 장애면 통과
 *   <li>⑤ Lua 기록({@link RedisViewStore#record}) — 장애면 건너뜀
 * </ol>
 *
 * Redis 메모리 부족(503 {@code AUTOSAVE_UNAVAILABLE})도 잡아 건너뛴다 — 조회 기록은 어떤 경우에도 상세를 막지 않는다(research
 * R13). 조회수 로그는 DEBUG로 글 번호와 {@link ViewOutcome}만 남긴다. 트랜잭션을 열지 않는다(Redis 쓰기는 트랜잭션 밖).
 */
@Service
public class ViewRecordService {

    private static final Logger log = LoggerFactory.getLogger(ViewRecordService.class);

    /** 요청 제한 키 접두어. 뒤에는 방문자 키(회원 번호 또는 해시)만 붙는다 — IP·쿠키 값은 붙지 않는다. */
    public static final String RATE_LIMIT_PREFIX = "ratelimit:view:";

    /**
     * 조회 기록 요청에서 읽는 값.
     *
     * @param userAgent {@code User-Agent}
     * @param secPurpose {@code Sec-Purpose}
     * @param purpose {@code Purpose}
     * @param vid {@code vid} 쿠키 값
     * @param clientIp {@code ClientIp.of(request)}
     */
    public record ViewRequest(
            String userAgent, String secPurpose, String purpose, String vid, String clientIp) {}

    private final PostReadService postReadService;
    private final VisitorKeyResolver visitorKeys;
    private final RateLimiter rateLimiter;
    private final RedisViewStore store;
    private final ViewProperties properties;
    private final ViewExclusion exclusion;
    private final Clock clock;
    private final ZoneId zone;

    public ViewRecordService(
            PostReadService postReadService,
            VisitorKeyResolver visitorKeys,
            RateLimiter rateLimiter,
            RedisViewStore store,
            ViewProperties properties,
            ResourceLoader resourceLoader,
            Clock clock,
            ZoneId serviceZoneId) {
        this.postReadService = postReadService;
        this.visitorKeys = visitorKeys;
        this.rateLimiter = rateLimiter;
        this.store = store;
        this.properties = properties;
        this.exclusion =
                ViewExclusion.fromResource(
                        resourceLoader.getResource(properties.botUserAgentWords()));
        this.clock = clock;
        this.zone = serviceZoneId;
    }

    /**
     * @throws PostNotFoundException 볼 수 없는 글 (404)
     * @throws com.team.blog.shared.error.TooManyRequestsException 같은 방문자 1분 60번 초과 (429)
     */
    public ViewOutcome record(Viewer viewer, long postId, ViewRequest request) {
        PostView post = postReadService.requireReadable(postId, viewer);
        if (post.status() != PostStatus.PUBLISHED) {
            throw new PostNotFoundException("조회 기록: 발행 글 아님");
        }
        ViewOutcome outcome = countOrSkip(viewer, post, request);
        log.debug("조회 기록 postId={} 결과={}", postId, outcome);
        return outcome;
    }

    private ViewOutcome countOrSkip(Viewer viewer, PostView post, ViewRequest request) {
        Optional<ViewOutcome> excluded =
                exclusion.reason(
                        post, viewer, request.userAgent(), request.secPurpose(), request.purpose());
        if (excluded.isPresent()) {
            return excluded.get();
        }
        LocalDate today = LocalDate.now(clock.withZone(zone));
        try {
            Optional<String> visitorKey =
                    visitorKeys.resolve(
                            viewer, request.vid(), request.clientIp(), request.userAgent(), today);
            if (visitorKey.isEmpty()) {
                return ViewOutcome.SKIPPED_REDIS;
            }
            ViewProperties.RateLimit limit = properties.rateLimit();
            rateLimiter.acquireOrThrow(
                    RATE_LIMIT_PREFIX + visitorKey.get(), limit.limit(), limit.window());
            RecordOutcome recorded =
                    store.record(
                            post.id(),
                            visitorKey.get(),
                            today,
                            properties.dedupeWindow(),
                            properties.maxPerWindow());
            return switch (recorded) {
                case COUNTED -> ViewOutcome.COUNTED;
                case DUPLICATE -> ViewOutcome.DUPLICATE;
                case SKIPPED -> ViewOutcome.SKIPPED_REDIS;
            };
        } catch (AutosaveUnavailableException e) {
            // Redis 메모리 부족 — 조회 기록은 건너뛰고 같은 204 (research R13)
            return ViewOutcome.SKIPPED_REDIS;
        }
    }
}
