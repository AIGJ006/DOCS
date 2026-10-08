package com.team.blog.category.application;

import com.team.blog.category.infra.CategoryRepository;
import com.team.blog.post.application.PostCategoryAssignment;
import com.team.blog.shared.security.AccountStatusGuard;
import com.team.blog.shared.security.ActionKind;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 글의 카테고리 지정·읽기 (017 US2, FR-020~FR-022, research R5). 발행과 별개인 즉시 저장 — 편집 버전·"수정됨"·이벤트가 없다.
 *
 * <p>판정 순서 (004와 같음): ① 비회원 401(컨트롤러 {@code @CurrentUser}) → ② 계정 상태 403({@code CONTENT_WRITE}) →
 * ③④ 내 글 행 잠금(없음·남의 글·휴지통 같은 404) → ⑤ 카테고리가 내 것인지(400 {@code INVALID_CATEGORY}, 칸 {@code
 * categoryId}).
 */
@Service
public class PostCategoryService {

    private final AccountStatusGuard accountStatusGuard;
    private final PostCategoryAssignment posts;
    private final CategoryRepository categories;

    public PostCategoryService(
            AccountStatusGuard accountStatusGuard,
            PostCategoryAssignment posts,
            CategoryRepository categories) {
        this.accountStatusGuard = accountStatusGuard;
        this.posts = posts;
        this.categories = categories;
    }

    /**
     * @param categoryId 분류 없음이면 {@code null}
     * @return 저장한 카테고리
     */
    @Transactional
    public Long assign(long memberId, long postId, Long categoryId) {
        accountStatusGuard.requireActive(memberId, ActionKind.CONTENT_WRITE);
        Long before = posts.lockOwned(postId, memberId);
        if (categoryId != null && categories.findOwned(memberId, categoryId).isEmpty()) {
            throw CategoryService.invalidCategory("categoryId");
        }
        if (!java.util.Objects.equals(before, categoryId)) {
            posts.assign(postId, categoryId);
        }
        return categoryId;
    }

    /** 작성자 화면용 지금 카테고리 (남의 글·휴지통 404). */
    @Transactional(readOnly = true)
    public Long current(long memberId, long postId) {
        return posts.currentOf(postId, memberId);
    }
}
