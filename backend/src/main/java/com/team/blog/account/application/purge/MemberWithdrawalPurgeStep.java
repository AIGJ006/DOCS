package com.team.blog.account.application.purge;

import com.team.blog.account.application.WithdrawalPolicy;
import com.team.blog.account.infra.WithdrawalMemberRepository;
import com.team.blog.shared.application.withdraw.WithdrawalPurgeStep;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴 정리 order 90 (마지막): 회원 익명화 (015 T049, contracts/purge-steps.md §2-1). 주소·역할·상태·신청 시각·설정·가입일과
 * 동의·정지 이력은 남긴다. 바뀐 행이 1이 아니면 예외 → 그 회원 정리 전체 롤백.
 */
@Component
public class MemberWithdrawalPurgeStep implements WithdrawalPurgeStep {

    private final WithdrawalMemberRepository members;
    private final WithdrawalPolicy policy;

    public MemberWithdrawalPurgeStep(WithdrawalMemberRepository members, WithdrawalPolicy policy) {
        this.members = members;
        this.policy = policy;
    }

    @Override
    public int order() {
        return 90;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void purge(long memberId) {
        members.anonymize(memberId, policy.now());
    }
}
