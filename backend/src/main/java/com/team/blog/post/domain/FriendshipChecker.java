package com.team.blog.post.domain;

/**
 * 두 회원이 수락된 친구인가 (004 US8, research R-14). 친구 관계는 001 소유이고 post 모듈은 이 좁은 읽기 창구만 쓴다 — 001 {@code
 * FriendshipQueryService}가 생기면 그것을 감싼 구현으로 바꿀 수 있다. 판정 기준은 {@code friendship}의 {@code member_a_id =
 * LEAST(a, b) AND member_b_id = GREATEST(a, b) AND status = 'ACCEPTED'}다.
 */
public interface FriendshipChecker {

    /** 수락된 친구면 {@code true}. 같은 회원이거나 요청 중({@code PENDING})이면 {@code false}. */
    boolean areFriends(long memberId, long otherMemberId);
}
