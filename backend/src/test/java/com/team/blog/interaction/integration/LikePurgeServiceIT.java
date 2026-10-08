package com.team.blog.interaction.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team.blog.interaction.application.LikePurgeService;
import com.team.blog.interaction.support.LikeEventProbe;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.fixture.PostFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

/** 탈퇴 정리 — 좋아요 (009 T040, contracts/view-pipeline.md §6, 015 LikeWithdrawalPurgeStep order 30). */
class LikePurgeServiceIT extends IntegrationTestBase {

    @Autowired LikePurgeService service;
    @Autowired LikeEventProbe probe;
    @Autowired TransactionTemplate tx;

    private long leaving;
    private long staying;
    private long postA;
    private long postB;
    private long postC;

    @BeforeEach
    void setUp() {
        long author = members().member().create();
        leaving = members().member().create();
        staying = members().member().create();
        PostFixtures posts = new PostFixtures(jdbc);
        postA = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        postB = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        postC = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        like(postA, leaving);
        like(postA, staying);
        like(postB, leaving);
        like(postC, staying);
        probe.arm();
    }

    private void like(long postId, long memberId) {
        jdbc.update("INSERT INTO post_like (post_id, member_id) VALUES (?, ?)", postId, memberId);
        jdbc.update("UPDATE post SET like_count = like_count + 1 WHERE id = ?", postId);
    }

    private int likeCount(long postId) {
        return jdbc.queryForObject(
                "SELECT like_count FROM post WHERE id = ?", Integer.class, postId);
    }

    private long likesOf(long memberId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM post_like WHERE member_id = ?", Long.class, memberId);
    }

    @Test
    void 그_회원_좋아요를_모두_지우고_글별_수를_줄인다() {
        Integer deleted = tx.execute(status -> service.purgeByMember(leaving));

        assertThat(deleted).isEqualTo(2);
        assertThat(likesOf(leaving)).isZero();
        assertThat(likeCount(postA)).isOne();
        assertThat(likeCount(postB)).isZero();
        assertThat(likeCount(postC)).as("다른 회원 좋아요는 그대로").isOne();
        assertThat(likesOf(staying)).isEqualTo(2);
        assertThat(probe.events()).as("이벤트 없음").isEmpty();
    }

    @Test
    void 좋아요가_없으면_0() {
        long nobody = members().member().create();
        Integer deleted = tx.execute(status -> service.purgeByMember(nobody));
        assertThat(deleted).isZero();
        assertThat(likeCount(postA)).isEqualTo(2);
    }

    @Test
    void 트랜잭션_밖에서_부르면_거절() {
        assertThatThrownBy(() -> service.purgeByMember(leaving))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(likesOf(leaving)).isEqualTo(2);
    }

    @Test
    void 롤백되면_그대로() {
        tx.executeWithoutResult(
                status -> {
                    service.purgeByMember(leaving);
                    status.setRollbackOnly();
                });
        assertThat(likesOf(leaving)).isEqualTo(2);
        assertThat(likeCount(postA)).isEqualTo(2);
    }
}
