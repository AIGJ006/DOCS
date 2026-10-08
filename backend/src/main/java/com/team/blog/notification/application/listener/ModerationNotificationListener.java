package com.team.blog.notification.application.listener;

import com.team.blog.notification.application.NotificationWriter;
import com.team.blog.shared.event.ContentHidden;
import com.team.blog.shared.event.ReportResolved;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 알림 리스너: 운영 알림 (contracts §1 — {@code ReportResolved}·{@code ContentHidden}, 014가 발행). 커밋 뒤 비동기,
 * 실패는 WARN만. {@code ContentUnhidden}은 구독하지 않는다 — 숨김 알림은 보여 줄 때 "지금은 다시 보여요"(FR-023).
 */
@Component
public class ModerationNotificationListener {

    private final NotificationWriter writer;

    public ModerationNotificationListener(NotificationWriter writer) {
        this.writer = writer;
    }

    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(ReportResolved e) {
        ListenerSupport.runSafely(
                "ReportResolved", e.reportId(), () -> writer.addReportResolved(e));
    }

    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(ContentHidden e) {
        ListenerSupport.runSafely("ContentHidden", e.targetId(), () -> writer.addContentHidden(e));
    }
}
