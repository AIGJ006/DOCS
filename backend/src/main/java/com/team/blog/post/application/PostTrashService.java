package com.team.blog.post.application;

import com.team.blog.post.application.exception.AutosaveUnavailableException;
import com.team.blog.post.domain.PostNotFoundException;
import com.team.blog.post.domain.PostStatus;
import com.team.blog.post.domain.TrashablePost;
import com.team.blog.post.domain.Visibility;
import com.team.blog.post.infra.TrashPostRepository;
import com.team.blog.shared.event.PostRestored;
import com.team.blog.shared.event.PostTrashed;
import com.team.blog.shared.infra.redis.RedisGuard;
import com.team.blog.shared.security.AccountStatusGuard;
import com.team.blog.shared.security.ActionKind;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 글 삭제(휴지통으로)·복구·영구 삭제 (006 T021·T029·T056, US1·US2·US4, 13 §2-3·§2-4, research R4~R8).
 *
 * <p>판정 순서(42 §3): 401(컨트롤러) → 403 계정 상태({@link ActionKind#CONTENT_CLEANUP} — 이메일 인증 전도 통과) → 404
 * 소유({@code author_id = 현재 사용자}로 행 잠금, 남의 글·없는 글 같은 응답) → 404 상태(복구·영구 삭제인데 휴지통이 아님). 같은 글에 대한 요청은
 * 행 잠금({@code FOR UPDATE})으로 직렬화된다(FR-036). 현재 사용자는 인자로만 받는다(constitution III).
 *
 * <p>로그에는 글 번호·회원 번호·결과만 남기고 제목·본문은 남기지 않는다.
 */
@Service
public class PostTrashService {

    private static final Logger log = LoggerFactory.getLogger(PostTrashService.class);

    /** 휴지통 이동 결과. */
    public sealed interface TrashOutcome {
        /** 휴지통으로 옮김(또는 이미 휴지통). */
        record Trashed(Instant purgeAt) implements TrashOutcome {}

        /** 빈 임시글이라 바로 완전히 지움. */
        record Purged() implements TrashOutcome {}
    }

    /** 복구 결과 — 원래 상태·공개 범위 (원래 탭을 고르는 데 쓴다). */
    public record RestoreOutcome(PostStatus status, Visibility visibility) {}

    private final AccountStatusGuard accountStatusGuard;
    private final TrashPostRepository trashPosts;
    private final PostPurgeService purgeService;
    private final AutosaveService autosaves;
    private final TrashProperties properties;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public PostTrashService(
            AccountStatusGuard accountStatusGuard,
            TrashPostRepository trashPosts,
            PostPurgeService purgeService,
            AutosaveService autosaves,
            TrashProperties properties,
            ApplicationEventPublisher events,
            Clock clock) {
        this.accountStatusGuard = accountStatusGuard;
        this.trashPosts = trashPosts;
        this.purgeService = purgeService;
        this.autosaves = autosaves;
        this.properties = properties;
        this.events = events;
        this.clock = clock;
    }

    /**
     * 글을 휴지통으로 옮긴다 ({@code DELETE /api/posts/{postId}}).
     *
     * <ol>
     *   <li>행 잠금. 없거나 남의 글이면 404
     *   <li>이미 휴지통이면 아무것도 바꾸지 않고 기존 {@code purgeAt}을 돌려준다(FR-025, 이벤트 없음)
     *   <li>Redis 자동 저장분을 DB에 반영(002 {@code flushNow}, 키 정리는 커밋 후). Redis 장애면 건너뛴다
     *   <li>반영한 값으로 빈 임시글이면 바로 완전 삭제(FR-020, 이벤트 없음)
     *   <li>아니면 {@code deleted_at = now}({@code updated_at} 그대로) + {@link PostTrashed}
     * </ol>
     */
    @Transactional
    public TrashOutcome trash(long me, long postId) {
        accountStatusGuard.requireActive(me, ActionKind.CONTENT_CLEANUP);
        TrashablePost post = lockOwned(postId, me, "휴지통 이동");
        if (post.isTrashed()) {
            log.info("휴지통 이동: postId={} authorId={} result=already", postId, me);
            return new TrashOutcome.Trashed(post.purgeAt(properties.retention()));
        }
        flushPendingAutosave(postId);
        TrashablePost flushed = lockOwned(postId, me, "휴지통 이동(반영 뒤)");
        if (flushed.isEmptyDraft()) {
            purgeService.purge(postId, me, false);
            log.info("휴지통 이동: postId={} authorId={} result=purged", postId, me);
            return new TrashOutcome.Purged();
        }
        Instant now = now();
        trashPosts.markTrashed(postId, now);
        events.publishEvent(new PostTrashed(postId, me, now));
        log.info("휴지통 이동: postId={} authorId={} result=trashed", postId, me);
        return new TrashOutcome.Trashed(now.plus(properties.retention()));
    }

    /**
     * 휴지통에서 복구한다 ({@code POST /api/posts/{postId}/restore}). {@code deleted_at = NULL}만 바꾸고 {@code
     * status}·{@code visibility}·{@code first_public_at}·{@code hidden_at}·{@code updated_at}은 그대로
     * 둬 원래 탭·원래 위치로 돌아간다(FR-027, 13 D-4). 휴지통에 없는 글·남의 글·없는 글은 404(FR-028).
     */
    @Transactional
    public RestoreOutcome restore(long me, long postId) {
        accountStatusGuard.requireActive(me, ActionKind.CONTENT_CLEANUP);
        TrashablePost post = lockOwned(postId, me, "복구");
        if (!post.isTrashed()) {
            throw new PostNotFoundException("복구: 휴지통 글 아님");
        }
        trashPosts.clearTrashed(postId);
        events.publishEvent(new PostRestored(postId, me, now()));
        log.info("복구: postId={} authorId={} result=restored", postId, me);
        return new RestoreOutcome(post.status(), post.visibility());
    }

    /**
     * 휴지통 글을 바로 완전히 지운다 (FR-029~035, 13 §2-4). 휴지통에 없는 글·남의 글·없는 글은 모두 같은 404다. 확인창은 화면이 띄운다. 딸린 행
     * 정리와 {@code PostPurged} 발행은 {@link PostPurgeService}가 한다.
     */
    @Transactional
    public void purgePermanently(long me, long postId) {
        accountStatusGuard.requireActive(me, ActionKind.CONTENT_CLEANUP);
        TrashablePost post = lockOwned(postId, me, "영구 삭제");
        if (!post.isTrashed()) {
            throw new PostNotFoundException("영구 삭제: 휴지통 글 아님");
        }
        purgeService.purge(postId, me, true);
        log.info("영구 삭제: postId={} authorId={}", postId, me);
    }

    private void flushPendingAutosave(long postId) {
        try {
            autosaves.flushNow(postId);
        } catch (AutosaveUnavailableException e) {
            log.warn("Redis 메모리 부족으로 자동 저장 반영을 건너뜁니다: postId={}", postId);
        } catch (RuntimeException e) {
            if (!RedisGuard.isRedisFailure(e)) {
                throw e;
            }
            log.warn("Redis 장애로 자동 저장 반영을 건너뜁니다: postId={}", postId);
        }
    }

    TrashablePost lockOwned(long postId, long me, String action) {
        return trashPosts
                .lockOwned(postId, me)
                .orElseThrow(() -> new PostNotFoundException(action + ": 내 글 아님"));
    }

    Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
