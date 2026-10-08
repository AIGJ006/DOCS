package com.team.blog.interaction.application;

import com.team.blog.interaction.domain.CommentReasonCode;
import com.team.blog.interaction.domain.CommentState;
import com.team.blog.interaction.domain.CommentText;
import com.team.blog.interaction.infra.CommentRepository;
import com.team.blog.interaction.infra.CommentRow;
import com.team.blog.post.application.PostCounterService;
import com.team.blog.post.application.PostReadService;
import com.team.blog.post.domain.PostNotFoundException;
import com.team.blog.post.domain.PostStatus;
import com.team.blog.post.infra.PostView;
import com.team.blog.shared.error.BusinessRuleException;
import com.team.blog.shared.error.NotFoundException;
import com.team.blog.shared.error.ValidationException;
import com.team.blog.shared.event.CommentCreated;
import com.team.blog.shared.event.CommentDeleted;
import com.team.blog.shared.infra.ratelimit.RateLimiter;
import com.team.blog.shared.security.AccountStatusGuard;
import com.team.blog.shared.security.ActionKind;
import com.team.blog.shared.security.Viewer;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 댓글 쓰기·고치기·지우기 (007 T031·T040, research R2·R4·R6·R7·R9).
 *
 * <p>판정 순서(FR-009, Clarifications Q2): 401(컨트롤러) → 403 계정 상태 → 404 글·댓글 → 400 내용·대상(수정은 409 숨김) →
 * 429 요청 제한. 요청 제한은 Redis를 쓰므로 트랜잭션 밖에서 세고, 앞 단계에서 걸린 요청은 세지 않는다. 그래서 트랜잭션 없이 한 번 미리 확인한 뒤 요청 제한을
 * 세고, 트랜잭션에서 잠금과 함께 다시 확인한다.
 *
 * <p>잠금은 항상 최상위 → 답글 순서다. 답글 작성은 최상위 {@code FOR SHARE}, 삭제는 최상위 {@code FOR UPDATE} — 삭제와 답글이 동시에 오면
 * 하나씩 처리된다(FR-014). 10초 중복 방지는 {@code pg_advisory_xact_lock}으로 같은 요청끼리 줄을 세운다(R7). 로그에 내용을 남기지 않는다.
 */
@Service
public class CommentService {

    private static final Logger log = LoggerFactory.getLogger(CommentService.class);

    private final AccountStatusGuard accountStatusGuard;
    private final PostReadService posts;
    private final PostCounterService counters;
    private final CommentRepository comments;
    private final CommentViewAssembler assembler;
    private final RateLimiter rateLimiter;
    private final CommentProperties properties;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;
    private final Clock clock;

    public CommentService(
            AccountStatusGuard accountStatusGuard,
            PostReadService posts,
            PostCounterService counters,
            CommentRepository comments,
            CommentViewAssembler assembler,
            RateLimiter rateLimiter,
            CommentProperties properties,
            ApplicationEventPublisher events,
            TransactionTemplate tx,
            Clock clock) {
        this.accountStatusGuard = accountStatusGuard;
        this.posts = posts;
        this.counters = counters;
        this.comments = comments;
        this.assembler = assembler;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.events = events;
        this.tx = tx;
        this.clock = clock;
    }

    /**
     * 작성 결과.
     *
     * @param created 새로 만들었으면 {@code true}(201), 10초 안 같은 요청이라 처음 댓글을 돌려주면 {@code false}(200)
     */
    public record CreateResult(CommentView comment, boolean created) {}

    /** 답글 자리: 부모(최상위)와 대상 회원. */
    private record Placement(long rootId, Long replyToMemberId, long targetId) {}

    /** 댓글·답글 쓰기 ({@code POST /api/posts/{postId}/comments}). */
    public CreateResult create(
            long postId, long me, String rawContent, Long replyToCommentId, Viewer viewer) {
        accountStatusGuard.requireActive(me, ActionKind.CONTENT_WRITE);
        PostView post = requireWritablePost(postId, viewer);
        String content = CommentText.normalize(rawContent);
        requireContent(content);
        Placement placement =
                replyToCommentId == null ? null : placeReply(post, me, replyToCommentId);
        rateLimiter.acquireOrThrow(
                "ratelimit:comment:" + me,
                properties.rateLimit().create().limit(),
                properties.rateLimit().create().window());

        Long parentId = placement == null ? null : placement.rootId();
        Long replyTo = placement == null ? null : placement.replyToMemberId();
        CreateResult result =
                tx.execute(
                        status -> {
                            comments.advisoryLock(
                                    CommentRepository.dedupeKey(
                                            me, postId, content, replyToCommentId));
                            var duplicate =
                                    comments.findRecentDuplicate(
                                            me,
                                            postId,
                                            content,
                                            parentId,
                                            replyTo,
                                            properties.dedupeWindow());
                            if (duplicate.isPresent()) {
                                return new CreateResult(
                                        assembler.single(post.authorId(), viewer, duplicate.get()),
                                        false);
                            }
                            Long parentAuthorId = null;
                            if (placement != null) {
                                parentAuthorId = lockReplyTarget(postId, placement);
                            }
                            CommentRow row =
                                    comments.insert(postId, me, parentId, replyTo, content);
                            counters.adjustCommentCount(postId, 1);
                            events.publishEvent(
                                    new CommentCreated(
                                            row.id(),
                                            postId,
                                            post.authorId(),
                                            me,
                                            parentId,
                                            parentAuthorId,
                                            replyTo,
                                            row.createdAt()));
                            return new CreateResult(
                                    assembler.single(post.authorId(), viewer, row), true);
                        });
        log.info(
                "댓글 작성: postId={} commentId={} authorId={} created={}",
                postId,
                result.comment().id(),
                me,
                result.created());
        return result;
    }

    /** 내 댓글 고치기 ({@code PATCH /api/comments/{commentId}}). */
    public CommentView edit(long commentId, long me, String rawContent, Viewer viewer) {
        accountStatusGuard.requireActive(me, ActionKind.CONTENT_WRITE);
        CommentRow found = requireOwnedAlive(comments.findOwned(commentId, me).orElse(null));
        PostView post = requireWritablePost(found.postId(), viewer);
        if (found.isHidden()) {
            throw new BusinessRuleException(CommentReasonCode.COMMENT_HIDDEN);
        }
        String content = CommentText.normalize(rawContent);
        requireContent(content);
        rateLimiter.acquireOrThrow(
                "ratelimit:comment-edit:" + me,
                properties.rateLimit().edit().limit(),
                properties.rateLimit().edit().window());
        CommentRow saved =
                tx.execute(
                        status -> {
                            CommentRow locked =
                                    requireOwnedAlive(
                                            comments.findOwnedForUpdate(commentId, me)
                                                    .orElse(null));
                            if (locked.isHidden()) {
                                throw new BusinessRuleException(CommentReasonCode.COMMENT_HIDDEN);
                            }
                            if (locked.content().equals(content)) {
                                return locked;
                            }
                            return comments.updateContent(commentId, content, now());
                        });
        log.info("댓글 수정: commentId={} authorId={}", commentId, me);
        return assembler.single(post.authorId(), viewer, saved);
    }

    /**
     * 내 댓글 지우기 ({@code DELETE /api/comments/{commentId}}). 글 읽기 확인을 하지 않는다 — 글이 비공개·휴지통·숨김이어도 내 댓글은
     * 지울 수 있다(research R9 제안). 답글 있는 최상위는 자리로 남기고, 그 밖은 행을 지운다. 자리인 최상위의 마지막 답글을 지우면 자리도 지운다.
     */
    public void delete(long commentId, long me) {
        accountStatusGuard.requireActive(me, ActionKind.CONTENT_CLEANUP);
        CommentRow found = requireOwnedAlive(comments.findOwned(commentId, me).orElse(null));
        long postAuthorId = posts.findAuthorId(found.postId()).orElse(0L);
        tx.executeWithoutResult(
                status -> {
                    Instant now = now();
                    CommentRow root =
                            comments.lockRootForUpdate(found.rootId())
                                    .orElseThrow(() -> new NotFoundException("댓글 삭제: 최상위 없음"));
                    CommentRow target;
                    if (found.isRoot()) {
                        target = root;
                        if (target.authorId() != me) {
                            throw new NotFoundException("댓글 삭제: 내 댓글 아님");
                        }
                    } else {
                        target =
                                comments.findOwnedForUpdate(commentId, me)
                                        .orElseThrow(() -> new NotFoundException("댓글 삭제: 없음"));
                    }
                    requireOwnedAlive(target);
                    boolean counted = !target.isHidden();
                    if (target.isRoot() && comments.countReplies(target.id()) > 0) {
                        comments.markDeletedPlaceholder(target.id(), now);
                    } else {
                        comments.delete(target.id());
                    }
                    if (counted) {
                        counters.adjustCommentCount(target.postId(), -1);
                    }
                    events.publishEvent(
                            new CommentDeleted(
                                    target.id(),
                                    target.postId(),
                                    postAuthorId,
                                    me,
                                    target.parentId(),
                                    now));
                    if (!target.isRoot()
                            && root.isDeleted()
                            && comments.countReplies(root.id()) == 0) {
                        comments.delete(root.id());
                        events.publishEvent(
                                new CommentDeleted(
                                        root.id(),
                                        root.postId(),
                                        postAuthorId,
                                        root.authorId(),
                                        null,
                                        now));
                    }
                });
        log.info("댓글 삭제: commentId={} authorId={}", commentId, me);
    }

    // ---- 판정 ----

    /** 쓰기·수정할 수 있는 글: 읽을 수 있고 발행됐고 숨김이 아님. 아니면 같은 404. */
    private PostView requireWritablePost(long postId, Viewer viewer) {
        PostView post = posts.requireReadable(postId, viewer);
        if (post.status() != PostStatus.PUBLISHED || post.isHidden()) {
            throw new PostNotFoundException("댓글 쓰기: 발행되지 않았거나 숨긴 글");
        }
        return post;
    }

    private void requireContent(String content) {
        CommentText.check(content, properties.contentMax())
                .ifPresent(
                        code -> {
                            throw new ValidationException(List.of(code.at("content")));
                        });
    }

    /** 대상 미리 확인 (빠른 거부, research R4). 트랜잭션에서 다시 확인한다. */
    private Placement placeReply(PostView post, long me, long targetId) {
        CommentRow target =
                comments.find(targetId)
                        .filter(t -> t.postId() == post.id())
                        .orElseThrow(CommentService::targetUnavailable);
        if (!acceptsReply(target)) {
            throw targetUnavailable();
        }
        Long replyTo = !target.isRoot() && target.authorId() != me ? target.authorId() : null;
        return new Placement(target.rootId(), replyTo, target.id());
    }

    /** 트랜잭션 안: 최상위 → 대상 순서로 공유 잠금하고 다시 확인한다. 최상위 작성자 번호를 돌려준다. */
    private Long lockReplyTarget(long postId, Placement placement) {
        CommentRow root =
                comments.lockRootShared(placement.rootId(), postId)
                        .orElseThrow(CommentService::targetUnavailable);
        if (placement.targetId() == root.id()) {
            if (root.isDeleted() || root.isHidden()) {
                throw targetUnavailable();
            }
        } else {
            CommentRow target =
                    comments.lockShared(placement.targetId())
                            .filter(t -> t.parentId() != null && t.parentId() == root.id())
                            .orElseThrow(CommentService::targetUnavailable);
            if (target.isDeleted() || target.isHidden()) {
                throw targetUnavailable();
            }
        }
        return root.authorId();
    }

    private boolean acceptsReply(CommentRow target) {
        if (target.isDeleted() || target.isHidden()) {
            return false;
        }
        return assembler.isActiveAuthor(target.authorId())
                && CommentState.of(false, target.deletedAt(), target.hiddenAt()).acceptsReply();
    }

    private static ValidationException targetUnavailable() {
        return new ValidationException(
                List.of(CommentReasonCode.REPLY_TARGET_UNAVAILABLE.at("replyToCommentId")));
    }

    /** 내 댓글이고 자리가 아님. 아니면 404 (남의 댓글·없는 댓글·이미 지운 자리 같은 응답). */
    private static CommentRow requireOwnedAlive(CommentRow row) {
        if (row == null || row.isDeleted()) {
            throw new NotFoundException("댓글: 없음·남의 것·지운 자리");
        }
        return row;
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
