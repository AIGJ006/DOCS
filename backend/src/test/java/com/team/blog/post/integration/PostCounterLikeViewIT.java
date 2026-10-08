package com.team.blog.post.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team.blog.post.application.PostCounterService;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.fixture.PostFixtures;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 글 카운터의 좋아요·조회 메서드 (009 T004, contracts/view-pipeline.md §7, research R2·R9). 007의 댓글 메서드
 * 시험({@code PostCounterServiceIT})과 파일을 나눠 두 브랜치가 같은 파일을 새로 만들며 부딪히지 않게 했다.
 */
class PostCounterLikeViewIT extends IntegrationTestBase {

    @Autowired PostCounterService counters;
    @Autowired TransactionTemplate tx;

    private long author;
    private long postId;

    @BeforeEach
    void setUp() {
        author = members().member().create();
        postId = new PostFixtures(jdbc).create(author, PostFixtures.State.PUBLISHED_PUBLIC);
    }

    private int likeCountInDb(long id) {
        return jdbc.queryForObject("SELECT like_count FROM post WHERE id = ?", Integer.class, id);
    }

    private long viewCountInDb(long id) {
        return jdbc.queryForObject("SELECT view_count FROM post WHERE id = ?", Long.class, id);
    }

    private void like(long post, long member) {
        jdbc.update("INSERT INTO post_like (post_id, member_id) VALUES (?, ?)", post, member);
    }

    @Test
    void adjustLikeCount는_더하고_빼며_likeCount로_읽는다() {
        tx.executeWithoutResult(s -> counters.adjustLikeCount(postId, 1));
        tx.executeWithoutResult(s -> counters.adjustLikeCount(postId, 1));
        assertThat(likeCountInDb(postId)).isEqualTo(2);
        tx.executeWithoutResult(s -> counters.adjustLikeCount(postId, -1));
        assertThat(counters.likeCount(postId)).isEqualTo(1);
    }

    @Test
    void adjustLikeCount는_트랜잭션_밖에서_부를_수_없다() {
        assertThatThrownBy(() -> counters.adjustLikeCount(postId, 1))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(likeCountInDb(postId)).isZero();
    }

    @Test
    void addViews는_view_count만_늘리고_updated_at은_그대로() {
        Instant before =
                jdbc.queryForObject(
                                "SELECT updated_at FROM post WHERE id = ?", Timestamp.class, postId)
                        .toInstant();
        Boolean ok = tx.execute(s -> counters.addViews(postId, 7));
        assertThat(ok).isTrue();
        assertThat(viewCountInDb(postId)).isEqualTo(7);
        assertThat(
                        jdbc.queryForObject(
                                        "SELECT updated_at FROM post WHERE id = ?",
                                        Timestamp.class,
                                        postId)
                                .toInstant())
                .isEqualTo(before);
    }

    @Test
    void addViews는_없는_글이면_false() {
        long missing = new PostFixtures(jdbc).nonexistentId();
        Boolean ok = tx.execute(s -> counters.addViews(missing, 3));
        assertThat(ok).isFalse();
        assertThatThrownBy(() -> counters.addViews(postId, 1))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void adjustLikeCounts는_여러_글을_한_번에_바꾼다() {
        long other = new PostFixtures(jdbc).create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        jdbc.update("UPDATE post SET like_count = 5 WHERE id IN (?, ?)", postId, other);
        tx.executeWithoutResult(s -> counters.adjustLikeCounts(Map.of(postId, -2, other, -5)));
        assertThat(likeCountInDb(postId)).isEqualTo(3);
        assertThat(likeCountInDb(other)).isZero();
        assertThatThrownBy(() -> counters.adjustLikeCounts(Map.of(postId, -1)))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void reconcileLikeCounts는_어긋난_글만_고치고_번호를_돌려준다() {
        long okPost = new PostFixtures(jdbc).create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        long m1 = members().member().create();
        long m2 = members().member().create();
        like(postId, m1);
        like(postId, m2);
        like(okPost, m1);
        jdbc.update("UPDATE post SET like_count = 9 WHERE id = ?", postId);
        jdbc.update("UPDATE post SET like_count = 1 WHERE id = ?", okPost);

        List<Long> fixed = counters.reconcileLikeCounts();

        assertThat(fixed).containsExactly(postId);
        assertThat(likeCountInDb(postId)).isEqualTo(2);
        assertThat(likeCountInDb(okPost)).isEqualTo(1);
        assertThat(counters.reconcileLikeCounts()).isEmpty();
    }
}
