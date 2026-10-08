package com.team.blog.notification.application;

import com.team.blog.notification.infra.NotificationRepository;
import com.team.blog.shared.error.NotFoundException;
import com.team.blog.shared.security.AccountStatusGuard;
import com.team.blog.shared.security.ActionKind;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 읽음·모두 읽음·삭제 (011 research R11, contracts §9). 계정 상태는 {@code ACCOUNT_WRITE}(이메일 인증 전도 통과, 남은 세션의
 * 정지는 403). 남의 알림·없는 번호는 관리자를 포함해 모두 같은 404다(FR-033).
 */
@Service
public class NotificationCommandService {

    private final NotificationRepository notifications;
    private final AccountStatusGuard guard;
    private final Clock clock;

    public NotificationCommandService(
            NotificationRepository notifications, AccountStatusGuard guard, Clock clock) {
        this.notifications = notifications;
        this.guard = guard;
        this.clock = clock;
    }

    @Transactional
    public void markRead(long me, long id) {
        guard.requireActive(me, ActionKind.ACCOUNT_WRITE);
        if (!notifications.markRead(id, me, clock.instant())) {
            throw new NotFoundException("알림 읽음: 없거나 남의 알림");
        }
    }

    @Transactional
    public int markAllRead(long me) {
        guard.requireActive(me, ActionKind.ACCOUNT_WRITE);
        return notifications.markAllRead(me, clock.instant());
    }

    @Transactional
    public void delete(long me, long id) {
        guard.requireActive(me, ActionKind.ACCOUNT_WRITE);
        if (!notifications.delete(id, me)) {
            throw new NotFoundException("알림 삭제: 없거나 남의 알림");
        }
    }
}
