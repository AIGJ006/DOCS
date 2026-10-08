package com.team.blog.interaction.application;

import com.team.blog.shared.application.withdraw.WithdrawalPurgeStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴 정리 order 30: 내가 누른 좋아요 삭제와 {@code like_count} 감소 (015 T052, contracts/purge-steps.md §2). 009
 * {@link LikePurgeService#purgeByMember}를 부른다. 이벤트는 내지 않는다.
 */
@Component
public class LikeWithdrawalPurgeStep implements WithdrawalPurgeStep {

    private static final Logger log = LoggerFactory.getLogger(LikeWithdrawalPurgeStep.class);

    private final LikePurgeService likes;

    public LikeWithdrawalPurgeStep(LikePurgeService likes) {
        this.likes = likes;
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void purge(long memberId) {
        int deleted = likes.purgeByMember(memberId);
        log.info("탈퇴 좋아요 정리: memberId={} deleted={}", memberId, deleted);
    }
}
