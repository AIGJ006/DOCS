package com.team.blog.notification.application.listener;

import com.team.blog.notification.application.NotificationWriter;
import com.team.blog.shared.event.CommentCreated;
import com.team.blog.shared.event.CommentDeleted;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 알림 리스너: 댓글·답글 (contracts §1 — {@code CommentCreated}·{@code CommentDeleted}). 커밋 뒤 비동기로 받고, 저장은
 * {@link NotificationWriter}의 자기 트랜잭션이 한다. 실패는 {@link ListenerSupport#runSafely}가 잡아 WARN만
 * 남긴다(FR-002).
 */
@Component
public class CommentNotificationListener {

    private final NotificationWriter writer;

    public CommentNotificationListener(NotificationWriter writer) {
        this.writer = writer;
    }

    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(CommentCreated e) {
        ListenerSupport.runSafely("CommentCreated", e.commentId(), () -> writer.addComment(e));
    }

    /** 댓글 삭제(자리로 남김 포함) — 그 댓글의 댓글·답글 알림 삭제 (contracts §7-1). */
    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(CommentDeleted e) {
        ListenerSupport.runSafely(
                "CommentDeleted",
                e.commentId(),
                () -> writer.removeCommentNotifications(e.commentId()));
    }
}
