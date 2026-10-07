package com.team.blog.post.application.port;

/**
 * 내가 이 글에 좋아요를 눌렀는지. <b>009 좋아요·조회수 기능이 소유를 넘겨받는다</b>(그때까지 005 기본 구현이 항상 {@code false}). 비회원·작성자
 * 본인에게는 호출하지 않는다.
 */
public interface PostLikeStatusQuery {

    boolean isLikedBy(long postId, long memberId);
}
