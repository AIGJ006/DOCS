package com.team.blog.category.application;

import com.team.blog.category.infra.CategoryRepository;
import com.team.blog.post.application.port.PostCategoryPathQuery;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** 005 글 상세의 카테고리 경로 포트 구현 (017 research R9). SQL 1번. */
@Component
public class CategoryPathQueryAdapter implements PostCategoryPathQuery {

    private final CategoryRepository categories;

    public CategoryPathQueryAdapter(CategoryRepository categories) {
        this.categories = categories;
    }

    @Override
    public Optional<CategoryPath> pathOf(long postId) {
        return categories
                .pathOfPost(postId)
                .map(
                        row ->
                                new CategoryPath(
                                        row.id(),
                                        row.name(),
                                        row.parentId() == null
                                                ? null
                                                : new Parent(row.parentId(), row.parentName())));
    }
}
