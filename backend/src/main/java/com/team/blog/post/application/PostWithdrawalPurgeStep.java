package com.team.blog.post.application;

import com.team.blog.shared.application.withdraw.WithdrawalPurgeStep;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴 정리 order 10: 내 글 전부(휴지통·임시·숨김 포함) 완전 삭제 (015 T050, contracts/purge-steps.md §2). 006 {@link
 * PostPurgeService#purgeAllByAuthor}가 글마다 신고·사진 단계와 {@code PostPurged}를 처리하고 지운 글 수를 INFO로 남긴다. 남이
 * 쓴 댓글·좋아요·알림은 CASCADE로 함께 사라지므로 20·30·70보다 먼저 돈다.
 */
@Component
public class PostWithdrawalPurgeStep implements WithdrawalPurgeStep {

    private final PostPurgeService posts;

    public PostWithdrawalPurgeStep(PostPurgeService posts) {
        this.posts = posts;
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void purge(long memberId) {
        posts.purgeAllByAuthor(memberId);
    }
}
