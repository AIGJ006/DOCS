package com.team.blog.post.application.port;

/**
 * 내가 이 작성자를 팔로우 중인지. <b>010 팔로우·피드 기능이 소유를 넘겨받는다</b>(그때까지 005 기본 구현이 항상 {@code false}). 비회원·작성자
 * 본인에게는 호출하지 않는다.
 */
public interface AuthorFollowStatusQuery {

    boolean isFollowing(long followerId, long followeeId);
}
