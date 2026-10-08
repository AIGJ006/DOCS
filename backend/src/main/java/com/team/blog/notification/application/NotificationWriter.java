package com.team.blog.notification.application;

import com.team.blog.interaction.application.CommentQueryService;
import com.team.blog.interaction.application.FollowQueryService;
import com.team.blog.interaction.application.LikeQueryService;
import com.team.blog.notification.domain.GroupKey;
import com.team.blog.notification.domain.NotificationType;
import com.team.blog.notification.infra.NotificationRepository;
import com.team.blog.post.application.PostReadService;
import com.team.blog.shared.event.CommentCreated;
import com.team.blog.shared.security.Viewer;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 저장 (011 research R3). 메서드마다 자기 트랜잭션({@code REQUIRES_NEW})이라 실패한 알림 하나가 통째로 롤백되고, 리스너가 예외를 잡아
 * 원래 행동에 영향을 주지 않는다. 저장 전에 공통 제외 규칙({@link NotificationEligibility})과 원래 상태가 아직 있는지를 다시
 * 확인한다(FR-006). 저장 시각은 처리 시각({@link Clock})이다 — 목록 정렬이 "알림이 생긴 순서"가 되게(contracts §3).
 */
@Service
public class NotificationWriter {

    private final NotificationRepository notifications;
    private final NotificationEligibility eligibility;
    private final CommentQueryService comments;
    private final LikeQueryService likes;
    private final FollowQueryService follows;
    private final PostReadService posts;
    private final NotificationProperties properties;
    private final Clock clock;

    public NotificationWriter(
            NotificationRepository notifications,
            NotificationEligibility eligibility,
            CommentQueryService comments,
            LikeQueryService likes,
            FollowQueryService follows,
            PostReadService posts,
            NotificationProperties properties,
            Clock clock) {
        this.notifications = notifications;
        this.eligibility = eligibility;
        this.comments = comments;
        this.likes = likes;
        this.follows = follows;
        this.posts = posts;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 댓글·답글 알림 (research R6). 답글이면 대상 {@code R = replyToMemberId ?? parentAuthorId}에게 {@code
     * REPLY}, 글 작성자에게 {@code COMMENT}(글 작성자 = R이면 만들지 않음). 받는 사람마다 공통 제외 규칙을 따로 적용한다.
     *
     * @return 만든 알림 수
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int addComment(CommentCreated e) {
        if (!comments.isActive(e.commentId())) {
            return 0;
        }
        Instant now = clock.instant();
        int created = 0;
        Long replyTarget = null;
        if (e.parentId() != null) {
            replyTarget = e.replyToMemberId() != null ? e.replyToMemberId() : e.parentAuthorId();
            if (replyTarget != null) {
                created += single(NotificationType.REPLY, replyTarget, e, now);
            }
        }
        if (replyTarget == null || replyTarget != e.postAuthorId()) {
            created += single(NotificationType.COMMENT, e.postAuthorId(), e, now);
        }
        return created;
    }

    private int single(NotificationType type, long receiverId, CommentCreated e, Instant now) {
        if (!eligibility.allows(type, receiverId, e.authorId(), e.postId())) {
            return 0;
        }
        notifications.insertSingle(
                receiverId, type, e.postId(), e.commentId(), null, null, e.authorId(), 1, now);
        return 1;
    }

    /** 좋아요 묶음에 더하기 (contracts §4). 좋아요가 이미 취소됐으면 만들지 않는다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean addLike(long receiverId, long actorId, long postId) {
        if (!eligibility.allows(NotificationType.LIKE, receiverId, actorId, postId)
                || !likes.isLiked(postId, actorId)) {
            return false;
        }
        return addToGroup(
                receiverId, NotificationType.LIKE, postId, GroupKey.like(postId), actorId, null);
    }

    /**
     * 새 팔로워 묶음에 더하기 (contracts §4, 같은 사람 {@code follow-dedup-window} 안 한 번). 이미 언팔로우했으면 만들지 않는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean addFollow(long receiverId, long actorId) {
        if (!eligibility.allows(NotificationType.FOLLOW, receiverId, actorId, null)
                || !follows.isFollowing(actorId, receiverId)) {
            return false;
        }
        Instant since = clock.instant().minus(properties.followDedupWindow());
        return addToGroup(
                receiverId, NotificationType.FOLLOW, null, GroupKey.follow(), actorId, since);
    }

    private boolean addToGroup(
            long receiverId,
            NotificationType type,
            Long postId,
            GroupKey key,
            long actorId,
            Instant dedupSince) {
        if (notifications.existsActor(receiverId, key, actorId, dedupSince)) {
            return false;
        }
        Instant now = clock.instant();
        long id = notifications.upsertUnreadGroup(receiverId, type, postId, key, now);
        if (notifications.insertActor(id, actorId, now)) {
            notifications.bumpGroup(id, actorId, now);
            return true;
        }
        notifications.deleteIfEmpty(id);
        return false;
    }

    /** 좋아요 취소 — 안 읽은 묶음에서 뺀다 (contracts §5). 제외 규칙은 보지 않는다(빼는 일은 언제나 안전). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean removeLike(long receiverId, long actorId, long postId) {
        if (likes.isLiked(postId, actorId)) {
            // 취소 뒤 다시 누른 상태면 처리 순서가 바뀐 것 — 빼지 않는다
            return false;
        }
        return notifications.removeFromUnreadGroup(receiverId, GroupKey.like(postId), actorId);
    }

    /** 언팔로우 — 안 읽은 새 팔로워 묶음에서 뺀다 (contracts §5). 언팔로우 자체는 알리지 않는다(FR-016). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean removeFollow(long receiverId, long actorId) {
        if (follows.isFollowing(actorId, receiverId)) {
            return false;
        }
        return notifications.removeFromUnreadGroup(receiverId, GroupKey.follow(), actorId);
    }

    /**
     * 새 글 알림 (contracts §6). 처리 시점에 비회원 기준으로 읽을 수 없으면(이미 비공개·휴지통·숨김·작성자 유예) 만들지 않는다.
     *
     * @return 넣은 알림 수
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int addNewPost(long postId, long authorId) {
        if (!posts.isReadable(postId, Viewer.anonymous())) {
            return 0;
        }
        return notifications.insertNewPostForFollowers(postId, authorId, clock.instant());
    }

    /** 그 댓글의 댓글·답글 알림 삭제 (contracts §7-1). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int removeCommentNotifications(long commentId) {
        return notifications.deleteCommentNotifications(commentId);
    }
}
