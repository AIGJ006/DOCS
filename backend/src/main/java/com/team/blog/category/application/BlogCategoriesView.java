package com.team.blog.category.application;

import java.util.List;

/**
 * 블로그 카테고리 목록 (017 contracts {@code BlogCategories}).
 *
 * @param totalCount "분류 전체보기" 글 수 — 블로그 머리말 글 수와 같은 조건
 */
public record BlogCategoriesView(long totalCount, List<CategoryNode> items) {}
