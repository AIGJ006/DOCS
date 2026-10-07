package com.team.blog.post.domain;

/**
 * 글 공개 범위 (FR-001, research R-01). DB 문자열({@code post.visibility}, {@code
 * member.default_visibility})과 같은 이름이며 이 enum이 유일한 공개 범위 타입이다(001은 {@code
 * Member.defaultVisibility}를 문자열로 매핑한다).
 *
 * <p>공통 값은 {@link #PUBLIC}·{@link #PRIVATE}뿐이다. {@code FRIENDS}는 선택 구현자만 값과 {@code
 * FriendsVisibilityRule} Bean, Flyway {@code V{n}__friends.sql}을 함께 추가한다(US8, data-model §5).
 * {@code PROTECTED}는 쓰지 않는다.
 *
 * <p>요청 문자열을 이 enum으로 바꿀 때는 {@code valueOf}가 아니라 {@link VisibilityRegistry#require(String,
 * String)}를 쓴다 — 허용값은 등록된 {@link VisibilityRule} Bean 집합이다.
 */
public enum Visibility {
    /** 전체 공개. */
    PUBLIC,
    /** 나만 보기. */
    PRIVATE
}
