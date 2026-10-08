package com.team.blog.discovery.application;

/**
 * 카드 목록의 추가 조건 (005 카드 조회 + 008 태그, research R6). 노출 조건(004 {@code VisibilityFilter})은 이와 별개로 항상
 * 붙는다. 010 팔로우 피드 등 다른 목록도 필드를 더해 같이 쓴다.
 *
 * @param authorId 블로그 주인 (전체 목록이면 {@code null})
 * @param tagId 이 태그가 붙은 글만 (조건 없음이면 {@code null})
 */
public record CardFilter(Long authorId, Long tagId) {

    /** 전체 목록 (홈). */
    public static CardFilter all() {
        return new CardFilter(null, null);
    }

    /** 한 블로그의 글. */
    public static CardFilter author(long authorId) {
        return new CardFilter(authorId, null);
    }
}
