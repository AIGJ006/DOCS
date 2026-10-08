package com.team.blog.post.domain;

/**
 * 글 공개 범위 (FR-001, research R-01). DB 문자열({@code post.visibility}, {@code
 * member.default_visibility})과 같은 이름이며 이 enum이 유일한 공개 범위 타입이다(001은 {@code
 * Member.defaultVisibility}를 문자열로 매핑한다).
 *
 * <p>공통 값은 {@link #PUBLIC}·{@link #PRIVATE}다. {@link #FRIENDS}는 선택 구현(US8, data-model §5)이라 값만 두고
 * 기본으로 꺼져 있다 — {@code FriendsVisibilityRule} Bean은 {@code
 * blog.visibility.friends.enabled=true}({@code friends} 프로필)일 때만 등록되고 Flyway {@code
 * db/friends/V900__friends.sql}이 DB CHECK를 넓힌다. 끈 환경에서는 등록된 Rule이 없어 {@link
 * VisibilityRegistry#require}가 400 {@code INVALID_VISIBILITY}로 거부하고 DB CHECK도 막는다. {@code
 * PROTECTED}는 쓰지 않는다.
 *
 * <p>요청 문자열을 이 enum으로 바꿀 때는 {@code valueOf}가 아니라 {@link VisibilityRegistry#require(String,
 * String)}를 쓴다 — 허용값은 등록된 {@link VisibilityRule} Bean 집합이다.
 */
public enum Visibility {
    /** 전체 공개. */
    PUBLIC,
    /** 나만 보기. */
    PRIVATE,
    /** 친구 공개 (선택 구현, 기본 꺼짐 — 위 설명). */
    FRIENDS
}
