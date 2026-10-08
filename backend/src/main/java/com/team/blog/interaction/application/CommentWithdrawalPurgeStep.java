package com.team.blog.interaction.application;

import com.team.blog.shared.application.withdraw.WithdrawalPurgeStep;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴 정리 order 20: 남의 글에 쓴 내 댓글 정리 (015 T051, contracts/purge-steps.md §2). 007 {@link
 * CommentPurgeService#purgeByAuthor}가 답글 달린 최상위는 자리로 남기고 나머지를 지운 뒤 {@code comment_count}를 맞춘다. 이벤트는
 * 내지 않는다.
 */
@Component
public class CommentWithdrawalPurgeStep implements WithdrawalPurgeStep {

    private final CommentPurgeService comments;

    public CommentWithdrawalPurgeStep(CommentPurgeService comments) {
        this.comments = comments;
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void purge(long memberId) {
        comments.purgeByAuthor(memberId);
    }
}
