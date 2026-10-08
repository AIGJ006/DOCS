package com.team.blog.notification.application;

import com.team.blog.notification.infra.NotificationRepository;
import com.team.blog.shared.application.withdraw.WithdrawalPurgeStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴 정리 order 70: 알림 정리 (011 T056, research R14, contracts §11, 015 contracts/purge-steps.md §2).
 * 015 정리 작업의 회원 트랜잭션 안({@code MANDATORY})에서 돈다. 10(글)·20(댓글) 다음이라 내 글·내 댓글에 달린 알림은 이미 CASCADE로
 * 사라졌다.
 *
 * <ol>
 *   <li>받은 알림 삭제
 *   <li>남의 묶음에서 빼고 인원·마지막 행동자를 다시 계산, 0명이면 삭제 — 잠금·삭제·다시 계산·빈 묶음 삭제를 따로 실행한다(한 문장 CTE는 UPDATE가
 *       DELETE 전 상태를 봄)
 *   <li>내가 행동한 하나짜리 알림 삭제
 *   <li>끄기 설정 삭제
 * </ol>
 *
 * 멱등이다. 로그에는 회원 번호와 건수만 남긴다.
 */
@Component
public class NotificationWithdrawalPurgeStep implements WithdrawalPurgeStep {

    private static final Logger log =
            LoggerFactory.getLogger(NotificationWithdrawalPurgeStep.class);

    private final NotificationRepository notifications;

    public NotificationWithdrawalPurgeStep(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    @Override
    public int order() {
        return 70;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void purge(long memberId) {
        NotificationRepository.PurgeResult result = notifications.purgeMember(memberId);
        log.info(
                "탈퇴 알림 정리: memberId={} received={} groups={} emptied={} acted={}",
                memberId,
                result.received(),
                result.groups(),
                result.emptied(),
                result.acted());
    }
}
