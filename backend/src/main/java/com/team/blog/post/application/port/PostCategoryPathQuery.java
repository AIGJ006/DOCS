package com.team.blog.post.application.port;

import java.util.Optional;

/**
 * 글의 카테고리 경로 (017 research R9). 구현은 017 {@code CategoryPathQueryAdapter}.
 *
 * <p>조회가 실패하면 상세는 경로 없이 계속 응답한다(원칙 V).
 */
public interface PostCategoryPathQuery {

    /** 분류 없음이면 비어 있다. */
    Optional<CategoryPath> pathOf(long postId);

    /**
     * @param id 글의 카테고리
     * @param name 이름
     * @param parent 상위 (최상위면 {@code null})
     */
    record CategoryPath(long id, String name, Parent parent) {}

    record Parent(long id, String name) {}
}
