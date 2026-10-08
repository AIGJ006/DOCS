package com.team.blog.post.application.port;

/**
 * 내가 이 글에 좋아요를 눌렀는지. 구현은 009 좋아요·조회수 기능의 {@code interaction.application.LikeStatusQueryAdapter}다
 * ({@code post_like} PK 조회 1번). 비회원·작성자 본인에게는 호출하지 않는다.
 */
public interface PostLikeStatusQuery {

    boolean isLikedBy(long postId, long memberId);
}
