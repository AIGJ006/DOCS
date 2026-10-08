package com.team.blog.interaction.application;

import com.team.blog.interaction.infra.LikeRepository;
import com.team.blog.post.application.port.PostLikeStatusQuery;
import org.springframework.stereotype.Component;

/**
 * 글 상세의 "내가 눌렀는지" (009 T017, contracts/view-pipeline.md §6). 005가 두었던 기본 구현(항상 {@code false})을 넘겨받아
 * {@code post_like} PK를 {@code EXISTS}로 한 번 본다. 비회원·작성자 본인에게는 상세가 부르지 않는다.
 */
@Component
public class LikeStatusQueryAdapter implements PostLikeStatusQuery {

    private final LikeRepository likes;

    public LikeStatusQueryAdapter(LikeRepository likes) {
        this.likes = likes;
    }

    @Override
    public boolean isLikedBy(long postId, long memberId) {
        return likes.exists(postId, memberId);
    }
}
