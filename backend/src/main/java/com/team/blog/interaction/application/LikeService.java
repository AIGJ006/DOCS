package com.team.blog.interaction.application;

import com.team.blog.interaction.domain.LikeReasonCode;
import com.team.blog.interaction.infra.LikeRepository;
import com.team.blog.post.application.PostCounterService;
import com.team.blog.post.application.PostReadService;
import com.team.blog.post.domain.PostNotFoundException;
import com.team.blog.post.domain.PostStatus;
import com.team.blog.post.infra.PostView;
import com.team.blog.shared.error.ApiException;
import com.team.blog.shared.error.BusinessRuleException;
import com.team.blog.shared.error.CommonReasonCode;
import com.team.blog.shared.event.PostLiked;
import com.team.blog.shared.event.PostUnliked;
import com.team.blog.shared.infra.ratelimit.RateLimiter;
import com.team.blog.shared.security.AccountStatusGuard;
import com.team.blog.shared.security.ActionKind;
import com.team.blog.shared.security.Viewer;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 좋아요 지정·취소 (009 T015·T022, {@code PUT·DELETE /api/posts/{postId}/like}, research R1·R3,
 * FR-001~012).
 *
 * <p>판정 순서(README 2026-10-07, 42 §3, 처음 걸린 단계의 응답):
 *
 * <ol>
 *   <li>① 비회원 → 401 {@code LOGIN_REQUIRED}
 *   <li>② 계정 상태 — 001 {@link AccountStatusGuard}({@link ActionKind#CONTENT_WRITE}): 탈퇴 유예 → 정지 → 인증
 *       전, 403
 *   <li>③ 글 — 004 {@link PostReadService#requireReadable} + {@code PUBLISHED}. 볼 수 없는 글은 모두 같은
 *       404(작성자 본인의 임시글·휴지통 글도)
 *   <li>④ 자기 글 → 400 {@code CANNOT_LIKE_OWN_POST}
 *   <li>⑤ 요청 제한 {@code ratelimit:like:{memberId}} 좋아요·취소 합쳐 1분 60번 → 429 {@code TOO_MANY_REQUESTS}
 *       + {@code Retry-After}. 트랜잭션 밖이고, 앞 단계에서 걸린 요청은 세지 않는다. Redis 장애면 통과
 * </ol>
 *
 * 그다음 한 트랜잭션에서 {@code INSERT … ON CONFLICT DO NOTHING}(취소는 {@code DELETE})의 영향 행이 1일 때만 post 모듈
 * {@link PostCounterService#adjustLikeCount}로 수를 ±1 하고, 같은 트랜잭션에서 최신 수를 읽어 돌려준다. 실제로 바뀐 경우에만 {@link
 * PostLiked}/{@link PostUnliked}를 발행한다(커밋 후 전달). 관리자도 일반 회원과 같다(42 §7). 트랜잭션 안에서는 Redis를 쓰지 않는다.
 */
@Service
public class LikeService {

    private static final Logger log = LoggerFactory.getLogger(LikeService.class);

    /** 요청 제한 키 접두어 (001·002 규칙 {@code ratelimit:}, plan 설계 후 확인 2). */
    public static final String RATE_LIMIT_PREFIX = "ratelimit:like:";

    /**
     * 지금 상태 (응답 {@code {liked, likeCount}}).
     *
     * @param liked 이 회원이 지금 좋아요한 상태인가
     * @param likeCount 다른 사람의 변화까지 반영한 지금 좋아요 수
     */
    public record LikeState(boolean liked, int likeCount) {}

    private final AccountStatusGuard accountStatusGuard;
    private final PostReadService postReadService;
    private final RateLimiter rateLimiter;
    private final LikeRepository likes;
    private final PostCounterService counters;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;
    private final LikeProperties properties;
    private final Clock clock;

    public LikeService(
            AccountStatusGuard accountStatusGuard,
            PostReadService postReadService,
            RateLimiter rateLimiter,
            LikeRepository likes,
            PostCounterService counters,
            ApplicationEventPublisher events,
            TransactionTemplate tx,
            LikeProperties properties,
            Clock clock) {
        this.accountStatusGuard = accountStatusGuard;
        this.postReadService = postReadService;
        this.rateLimiter = rateLimiter;
        this.likes = likes;
        this.counters = counters;
        this.events = events;
        this.tx = tx;
        this.properties = properties;
        this.clock = clock;
    }

    /** 좋아요 상태로. 이미 좋아요면 변화·이벤트 없이 지금 상태. */
    public LikeState like(Viewer viewer, long postId) {
        return change(viewer, postId, true);
    }

    /** 취소 상태로. 이미 취소면 변화·이벤트 없이 지금 상태. */
    public LikeState unlike(Viewer viewer, long postId) {
        return change(viewer, postId, false);
    }

    private LikeState change(Viewer viewer, long postId, boolean like) {
        if (viewer == null || !viewer.isAuthenticated()) {
            throw new ApiException(CommonReasonCode.LOGIN_REQUIRED);
        }
        long me = viewer.id();
        accountStatusGuard.requireActive(me, ActionKind.CONTENT_WRITE);
        PostView post = postReadService.requireReadable(postId, viewer);
        if (post.status() != PostStatus.PUBLISHED) {
            throw new PostNotFoundException("좋아요: 발행 글 아님");
        }
        if (post.authorId() == me) {
            throw new BusinessRuleException(LikeReasonCode.CANNOT_LIKE_OWN_POST);
        }
        LikeProperties.RateLimit limit = properties.rateLimit();
        rateLimiter.acquireOrThrow(RATE_LIMIT_PREFIX + me, limit.limit(), limit.window());

        try {
            return tx.execute(status -> apply(post, me, like));
        } catch (DataIntegrityViolationException e) {
            // 판정 뒤 글이 완전 삭제돼 FK가 막은 경우 — 없는 글과 같은 404
            throw new PostNotFoundException("좋아요: 처리 중 글이 사라짐");
        }
    }

    private LikeState apply(PostView post, long me, boolean like) {
        long postId = post.id();
        int changed = like ? likes.insertIfAbsent(postId, me) : likes.deleteIfPresent(postId, me);
        if (changed == 1) {
            counters.adjustLikeCount(postId, like ? 1 : -1);
        }
        int count = counters.likeCount(postId);
        if (changed == 1) {
            Instant now = clock.instant();
            events.publishEvent(
                    like
                            ? new PostLiked(postId, post.authorId(), me, now)
                            : new PostUnliked(postId, post.authorId(), me, now));
        }
        log.debug("좋아요 {} postId={} changed={}", like ? "지정" : "취소", postId, changed == 1);
        return new LikeState(like, count);
    }
}
