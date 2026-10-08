package com.team.blog.interaction.application;

import com.team.blog.shared.application.withdraw.WithdrawalPurgeStep;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴 정리 order 65: 팔로우 양방향 삭제 (010 T041, 015 contracts/purge-steps.md §2, 010
 * contracts/follow-sql.md §7). 010 {@link FollowPurgeService#purgeByMember}에 맡긴다 — 그쪽이 회원 번호·지운 행
 * 수만 INFO로 남긴다. 이벤트는 내지 않는다.
 */
@Component
public class FollowWithdrawalPurgeStep implements WithdrawalPurgeStep {

    private final FollowPurgeService follows;

    public FollowWithdrawalPurgeStep(FollowPurgeService follows) {
        this.follows = follows;
    }

    @Override
    public int order() {
        return FollowPurgeService.WITHDRAWAL_PURGE_ORDER;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void purge(long memberId) {
        follows.purgeByMember(memberId);
    }
}
