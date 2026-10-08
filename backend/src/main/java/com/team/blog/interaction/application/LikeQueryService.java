package com.team.blog.interaction.application;

import com.team.blog.interaction.infra.LikeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 좋아요 조회 공개 Service (009 소유 — 011 알림이 처리 시점 확인용으로 먼저 만듦, 011 data-model §4). 다른 모듈은 {@code
 * post_like}를 직접 읽지 않고 이것을 부른다.
 */
@Service
@Transactional(readOnly = true)
public class LikeQueryService {

    private final LikeRepository likes;

    public LikeQueryService(LikeRepository likes) {
        this.likes = likes;
    }

    /** 그 회원이 지금 그 글을 좋아요한 상태인가 ({@code post_like} 행 있음). */
    public boolean isLiked(long postId, long memberId) {
        return likes.exists(postId, memberId);
    }
}
