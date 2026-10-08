package com.team.blog.category.application;

import java.util.List;

/**
 * 내 카테고리 나무 (017 contracts {@code MyCategories}).
 *
 * @param maxCount 만들 수 있는 최대 개수 ({@code blog.category.max-count})
 */
public record MyCategoriesView(int maxCount, List<CategoryNode> items) {}
