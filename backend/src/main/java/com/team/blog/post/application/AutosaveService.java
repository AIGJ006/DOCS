package com.team.blog.post.application;

import com.team.blog.post.application.exception.RateLimitedException;
import com.team.blog.post.application.exception.VersionConflictException;
import com.team.blog.post.config.PostAuthoringProperties;
import com.team.blog.post.domain.PostNotFoundException;
import com.team.blog.post.domain.PostStatus;
import com.team.blog.post.infra.AutosaveEntry;
import com.team.blog.post.infra.PostEditRepository;
import com.team.blog.post.infra.PostEditRepository.EditState;
import com.team.blog.post.infra.PostEditRepository.FlushTarget;
import com.team.blog.post.infra.PostRepository;
import com.team.blog.post.infra.RedisAutosaveStore;
import com.team.blog.post.infra.RedisAutosaveStore.SaveOutcome;
import com.team.blog.shared.infra.ratelimit.RateLimitResult;
import com.team.blog.shared.infra.ratelimit.RateLimiter;
import com.team.blog.shared.infra.redis.RedisGuard;
import com.team.blog.shared.security.AccountStatusGuard;
import com.team.blog.shared.security.ActionKind;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 자동 저장 (002 T078·T079, FR-007 ②·016·018·019·020, A-4, B-3 ①). 판정 순서: 401(컨트롤러) → 403 계정 상태 → 429
 * 요청 제한 → 404 소유(Redis 키 유무와 무관, 매 요청 DB 확인) → 400 길이 → 409 버전.
 *
 * <p>저장은 Redis Lua 한 번(현재 = max(Redis, DB))이고, Redis 장애면 DB에 바로 저장한다(잠금 + DB 버전 확인). Redis 메모리 부족은
 * 503으로 거부하고 DB로 우회하지 않는다({@code RedisGuard}).
 */
@Service
public class AutosaveService {

    private static final Logger log = LoggerFactory.getLogger(AutosaveService.class);

    private final AccountStatusGuard accountStatusGuard;
    private final RateLimiter rateLimiter;
    private final PostEditRepository edits;
    private final PostRepository posts;
    private final RedisAutosaveStore store;
    private final SavedContentImages images;
    private final PostAuthoringProperties properties;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final PostAuthoringMetrics metrics;

    public AutosaveService(
            AccountStatusGuard accountStatusGuard,
            RateLimiter rateLimiter,
            PostEditRepository edits,
            PostRepository posts,
            RedisAutosaveStore store,
            SavedContentImages images,
            PostAuthoringProperties properties,
            TransactionTemplate tx,
            Clock clock,
            PostAuthoringMetrics metrics) {
        this.metrics = metrics;
        this.accountStatusGuard = accountStatusGuard;
        this.rateLimiter = rateLimiter;
        this.edits = edits;
        this.posts = posts;
        this.store = store;
        this.images = images;
        this.properties = properties;
        this.tx = tx;
        this.clock = clock;
    }

    /** 자동 저장. 태그 등 다른 필드는 받지 않는다(FR-006). */
    public SaveResult autosave(
            long postId, long memberId, String title, String contentMd, long baseVersion) {
        accountStatusGuard.requireActive(memberId, ActionKind.CONTENT_WRITE);
        PostAuthoringProperties.RateLimit limit = properties.autosave().rateLimit();
        if (rateLimiter.tryAcquire("ratelimit:autosave:" + memberId, limit.limit(), limit.window())
                instanceof RateLimitResult.Denied denied) {
            throw new RateLimitedException(denied.retryAfterSeconds());
        }
        EditState state = requireOwned(postId, memberId);
        String t = title == null ? "" : title;
        String md = contentMd == null ? "" : contentMd;
        PostCommandService.requireLengths(t, md, properties.post());

        Instant now = PostCommandService.now(clock);
        SaveOutcome outcome =
                store.save(
                        postId,
                        memberId,
                        baseVersion,
                        state.dbVersion(),
                        t,
                        md,
                        now,
                        properties.autosave().redisTtl());
        return switch (outcome) {
            case SaveOutcome.Accepted accepted -> new SaveResult(accepted.version(), now);
            case SaveOutcome.Rejected rejected ->
                    throw new VersionConflictException(
                            rejected.redisCopy().orElseGet(state::dbCopy));
            case SaveOutcome.NotOwner notOwner ->
                    throw new PostNotFoundException("자동 저장: 보관분의 회원이 다름");
            case SaveOutcome.Unavailable unavailable ->
                    saveToDatabase(postId, memberId, t, md, baseVersion, now);
        };
    }

    EditState requireOwned(long postId, long memberId) {
        return edits.findOwnedEditState(postId, memberId)
                .orElseThrow(() -> new PostNotFoundException("저장: 내 글 아님"));
    }

    /**
     * Redis 장애 때의 DB 직접 저장 (FR-018, B-3 ①): 행 잠금 → DB 현재 버전 = max(post, post_draft) 확인 → 임시글은
     * {@code post}, 발행 글은 {@code post_draft}에 저장 + 작성자 사진 연결.
     */
    SaveResult saveToDatabase(
            long postId,
            long memberId,
            String title,
            String contentMd,
            long baseVersion,
            Instant now) {
        metrics.autosaveDbFallback();
        log.warn("Redis 장애로 DB에 바로 저장합니다: postId={} memberId={}", postId, memberId);
        List<String> keys = images.ownedKeys(contentMd, memberId);
        return tx.execute(
                status -> {
                    posts.findForUpdateByIdAndAuthorId(postId, memberId)
                            .orElseThrow(() -> new PostNotFoundException("저장: 잠금 시 내 글 아님"));
                    EditState state = requireOwned(postId, memberId);
                    if (state.dbVersion() != baseVersion) {
                        throw new VersionConflictException(state.dbCopy());
                    }
                    long version = state.dbVersion() + 1;
                    apply(state.postStatus(), postId, title, contentMd, version, now);
                    images.attach(postId, memberId, keys);
                    return new SaveResult(version, now);
                });
    }

    /** 버전 조건 반영: 임시글은 {@code post}, 발행 글은 {@code post_draft}. 바꾼 행 수. */
    int apply(
            PostStatus status,
            long postId,
            String title,
            String contentMd,
            long version,
            Instant savedAt) {
        return status == PostStatus.DRAFT
                ? edits.updateDraftPostIfNewer(postId, title, contentMd, version, savedAt)
                : edits.upsertWorkingCopyIfNewer(postId, title, contentMd, version, savedAt);
    }

    /**
     * 즉시 반영 (006용 공개 메서드, EV §4). 006이 휴지통 이동·완전 삭제 트랜잭션 안에서 부른다. Redis 보관분을 {@code deleted_at} 조건
     * 없이 바로 DB에 반영하고, 키 정리는 호출한 쪽 트랜잭션의 커밋 후로 등록한다(롤백이면 키를 남긴다). 트랜잭션 밖에서 부르면 반영 후 바로 정리한다. Redis
     * 장애면 아무것도 하지 않는다(1분 반영이 나중에 맡는다).
     */
    public void flushNow(long postId) {
        Optional<AutosaveEntry> found = store.find(postId);
        if (found.isEmpty()) {
            return;
        }
        AutosaveEntry entry = found.get();
        Optional<FlushTarget> target = edits.findFlushTarget(postId);
        if (target.isPresent()) {
            List<String> keys = images.ownedKeys(entry.contentMd(), target.get().authorId());
            apply(
                    target.get().status(),
                    postId,
                    entry.title(),
                    entry.contentMd(),
                    entry.version(),
                    savedAtOf(entry));
            images.attach(postId, target.get().authorId(), keys);
        }
        afterCommit(() -> store.release(postId, entry.version(), entry.version()));
    }

    static Instant savedAtOf(AutosaveEntry entry) {
        return entry.savedAt() != null ? entry.savedAt() : Instant.now();
    }

    /** 커밋 뒤 Redis 정리 (트랜잭션 경계 검사의 허용 목록, T119). 트랜잭션 밖이면 바로. */
    private static void afterCommit(Runnable action) {
        RedisGuard.runAfterCommit(action);
    }
}
