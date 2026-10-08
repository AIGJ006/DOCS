package com.team.blog.interaction.application;

import com.team.blog.interaction.infra.LikeRepository;
import com.team.blog.post.application.PostCounterService;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴 정리 — 좋아요 (009 T043, contracts/view-pipeline.md §6).
 *
 * <p>015가 부르는 서명: {@code int purgeByMember(long memberId)} — 015 {@code
 * LikeWithdrawalPurgeStep}(order 30)이 탈퇴 트랜잭션 안에서 부른다. 호출한 쪽 트랜잭션 안에서만 쓴다({@code MANDATORY}). 그 회원의
 * {@code post_like}를 모두 지우고 글별 {@code like_count}를 지운 수만큼 줄인다. {@code PostUnliked} 이벤트는 내지
 * 않는다(알림·검색 갱신 대상이 아님).
 */
@Service
public class LikePurgeService {

    private final LikeRepository likes;
    private final PostCounterService counters;

    public LikePurgeService(LikeRepository likes, PostCounterService counters) {
        this.likes = likes;
        this.counters = counters;
    }

    /**
     * @return 지운 좋아요 수
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int purgeByMember(long memberId) {
        List<Long> postIds = likes.deleteAllByMember(memberId);
        if (postIds.isEmpty()) {
            return 0;
        }
        Map<Long, Integer> deltas = new TreeMap<>();
        for (long postId : postIds) {
            deltas.merge(postId, -1, Integer::sum);
        }
        counters.adjustLikeCounts(deltas);
        return postIds.size();
    }
}
