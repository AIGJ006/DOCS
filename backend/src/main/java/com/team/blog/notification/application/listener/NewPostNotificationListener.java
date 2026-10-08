package com.team.blog.notification.application.listener;

import com.team.blog.notification.application.NotificationWriter;
import com.team.blog.shared.event.PostWentPublic;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 알림 리스너: 팔로워 새 글 (contracts §1 — {@code PostWentPublic}). 커밋 뒤 비동기로 받고, 저장은 {@link
 * NotificationWriter}의 자기 트랜잭션이 한다. 실패는 {@link ListenerSupport#runSafely}가 잡아 WARN만 남긴다(FR-002).
 */
@Component
public class NewPostNotificationListener {

    private final NotificationWriter writer;

    public NewPostNotificationListener(NotificationWriter writer) {
        this.writer = writer;
    }

    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(PostWentPublic e) {
        ListenerSupport.runSafely(
                "PostWentPublic", e.postId(), () -> writer.addNewPost(e.postId(), e.authorId()));
    }
}
