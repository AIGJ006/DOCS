package com.team.blog.category.application;

import java.util.List;

/**
 * 카테고리 나무 한 칸 (017 data-model §5). 하위의 {@code children}은 항상 빈 목록이다.
 *
 * @param postCount 내 관리 화면은 내 글 수, 블로그는 노출 글 수. 최상위는 하위 글을 포함한다(FR-029)
 */
public record CategoryNode(long id, String name, long postCount, List<CategoryNode> children) {}
