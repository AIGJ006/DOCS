package com.team.blog.discovery.application;

/**
 * 카드 목록의 추가 조건 (005 카드 조회 + 008 태그 + 팔로우 피드(010), 008 research R6·010 research R7). 노출 조건(004
 * {@code VisibilityFilter})은 이와 별개로 항상 붙는다.
 *
 * @param authorId 블로그 주인 (전체 목록이면 {@code null})
 * @param tagId 이 태그가 붙은 글만 (조건 없음이면 {@code null})
 * @param followerId 이 회원이 팔로우한 작성자의 글만 (010 피드, 조건 없음이면 {@code null})
 */
public record CardFilter(Long authorId, Long tagId, Long followerId) {

    /** 블로그·태그 조건 (008). 팔로우 조건 없음. */
    public CardFilter(Long authorId, Long tagId) {
        this(authorId, tagId, null);
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
