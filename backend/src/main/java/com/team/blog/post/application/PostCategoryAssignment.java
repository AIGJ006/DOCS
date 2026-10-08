package com.team.blog.post.application;

import com.team.blog.post.domain.PostNotFoundException;
import com.team.blog.post.infra.PostCategoryRepository;
import com.team.blog.post.infra.PostCategoryRepository.OwnedPostCategory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 글의 카테고리 쓰기 (017 research R5). {@code post} 테이블 쓰기라 post 모듈에 두고, 017 category 모듈이 카테고리 소유를 확인한 뒤
 * 부른다(원칙 II). 계정 상태·카테고리 확인은 부르는 쪽 몫이다.
 */
@Service
public class PostCategoryAssignment {

    private final PostCategoryRepository repository;

    public PostCategoryAssignment(PostCategoryRepository repository) {
        this.repository = repository;
    }

    /**
     * 내 글을 잠그고 지금 카테고리를 돌려준다(판정 ③④).
     *
     * @throws PostNotFoundException 없는 글·남의 글·휴지통 글 (같은 404)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Long lockOwned(long postId, long memberId) {
        // categoryId가 null(분류 없음)일 수 있어 Optional.map을 쓰지 않는다
        OwnedPostCategory owned =
                repository
                        .lockOwned(postId, memberId)
                        .orElseThrow(() -> new PostNotFoundException("카테고리 지정: 내 글 아님"));
        return owned.categoryId();
    }

    /** {@link #lockOwned}로 잠근 글의 카테고리를 바꾼다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void assign(long postId, Long categoryId) {
        repository.updateCategory(postId, categoryId);
    }

    /**
     * 내 글의 지금 카테고리 (작성자 화면용).
     *
     * @throws PostNotFoundException 없는 글·남의 글·휴지통 글
     */
    @Transactional(readOnly = true)
    public Long currentOf(long postId, long memberId) {
        OwnedPostCategory owned =
                repository
                        .findOwned(postId, memberId)
                        .orElseThrow(() -> new PostNotFoundException("카테고리 조회: 내 글 아님"));
        return owned.categoryId();
    }
}
