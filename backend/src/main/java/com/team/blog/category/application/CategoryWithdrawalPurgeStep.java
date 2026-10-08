package com.team.blog.category.application;

import com.team.blog.category.infra.CategoryRepository;
import com.team.blog.shared.application.withdraw.WithdrawalPurgeStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴 정리 order 15: 그 회원의 카테고리 전부 삭제 (017 FR-050, research R10). order 10이 글을 먼저 지우고, 남은 글이 있어도
 * {@code post.category_id}는 {@code ON DELETE SET NULL}이라 막히지 않는다. 다시 불려도 같은 결과(멱등).
 */
@Component
public class CategoryWithdrawalPurgeStep implements WithdrawalPurgeStep {

    private static final Logger log = LoggerFactory.getLogger(CategoryWithdrawalPurgeStep.class);

    private final CategoryRepository categories;

    public CategoryWithdrawalPurgeStep(CategoryRepository categories) {
        this.categories = categories;
    }

    @Override
    public int order() {
        return 15;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void purge(long memberId) {
        int deleted = categories.deleteAllByMember(memberId);
        log.info("탈퇴 정리 카테고리: memberId={} deleted={}", memberId, deleted);
    }
}
