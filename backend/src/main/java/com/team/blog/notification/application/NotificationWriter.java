package com.team.blog.notification.application;

import com.team.blog.notification.infra.NotificationRepository;
import java.time.Clock;
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
    private final Clock clock;

    public NotificationWriter(
            NotificationRepository notifications,
            NotificationEligibility eligibility,
            Clock clock) {
        this.notifications = notifications;
        this.eligibility = eligibility;
        this.clock = clock;
    }

    /** 그 댓글의 댓글·답글 알림 삭제 (contracts §7-1). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int removeCommentNotifications(long commentId) {
        return notifications.deleteCommentNotifications(commentId);
    }
}
