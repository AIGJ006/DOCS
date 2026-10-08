package com.team.blog.post.application.port;

/**
 * 내가 이 작성자를 팔로우 중인지 (005 글 상세 {@code viewer.followingAuthor}). 구현은 010 interaction {@code
 * AuthorFollowStatusQueryAdapter}다. 비회원·작성자 본인에게는 호출하지 않는다.
 */
public interface AuthorFollowStatusQuery {

    boolean isFollowing(long followerId, long followeeId);
}
