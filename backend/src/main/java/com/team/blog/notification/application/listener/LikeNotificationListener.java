package com.team.blog.notification.application.listener;

import com.team.blog.notification.application.NotificationWriter;
import com.team.blog.shared.event.PostLiked;
import com.team.blog.shared.event.PostUnliked;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 알림 리스너: 좋아요 묶음 (contracts §1 — {@code PostLiked}). 커밋 뒤 비동기로 받고, 저장은 {@link NotificationWriter}의
 * 자기 트랜잭션이 한다. 실패는 {@link ListenerSupport#runSafely}가 잡아 WARN만 남긴다(FR-002).
 */
@Component
public class LikeNotificationListener {

    private final NotificationWriter writer;

    public LikeNotificationListener(NotificationWriter writer) {
        this.writer = writer;
    }

    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(PostLiked e) {
        ListenerSupport.runSafely(
                "PostLiked",
                e.postId(),
                () -> writer.addLike(e.postAuthorId(), e.memberId(), e.postId()));
    }

    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(PostUnliked e) {
        ListenerSupport.runSafely(
                "PostUnliked",
                e.postId(),
                () -> writer.removeLike(e.postAuthorId(), e.memberId(), e.postId()));
    }
}
