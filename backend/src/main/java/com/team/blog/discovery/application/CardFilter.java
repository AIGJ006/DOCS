package com.team.blog.discovery.application;

import java.util.List;

/**
 * 카드 목록의 추가 조건 (005 카드 조회 + 008 태그 + 팔로우 피드(010), 008 research R6·010 research R7). 노출 조건(004
 * {@code VisibilityFilter})은 이와 별개로 항상 붙는다.
 *
 * @param authorId 블로그 주인 (전체 목록이면 {@code null})
 * @param tagId 이 태그가 붙은 글만 (조건 없음이면 {@code null})
 * @param followerId 이 회원이 팔로우한 작성자의 글만 (010 피드, 조건 없음이면 {@code null})
 * @param categoryIds 이 카테고리들 중 하나에 든 글만 (017 블로그 카테고리 필터 — 최상위면 자기 + 하위, 조건 없음이면 {@code null})
 */
public record CardFilter(Long authorId, Long tagId, Long followerId, List<Long> categoryIds) {

    public CardFilter {
        categoryIds = categoryIds == null ? null : List.copyOf(categoryIds);
    }

    /** 블로그·태그·팔로우 조건 (008·010). 카테고리 조건 없음. */
    public CardFilter(Long authorId, Long tagId, Long followerId) {
        this(authorId, tagId, followerId, null);
    }

    /** 블로그·태그 조건 (008). 팔로우 조건 없음. */
    public CardFilter(Long authorId, Long tagId) {
        this(authorId, tagId, null, null);
    }

    /** 한 블로그의 카테고리 글 (017). */
    public static CardFilter authorInCategories(long authorId, List<Long> categoryIds) {
        return new CardFilter(authorId, null, null, categoryIds);
    }

    /** 전체 목록 (홈). */
    public static CardFilter all() {
        return new CardFilter(null, null, null);
    }

    /** 한 블로그의 글. */
    public static CardFilter author(long authorId) {
        return new CardFilter(authorId, null, null);
    }

    /** 이 회원이 팔로우한 사람들의 글 (010 피드). */
    public static CardFilter followedBy(long followerId) {
        return new CardFilter(null, null, followerId);
    }
}
