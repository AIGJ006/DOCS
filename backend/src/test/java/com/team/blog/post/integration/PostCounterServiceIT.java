package com.team.blog.post.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team.blog.post.application.PostCounterService;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.SqlCounter;
import com.team.blog.support.fixture.PostFixtures;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

/** 글 카운터 공개 Service (007 T006, research R5). */
class PostCounterServiceIT extends IntegrationTestBase {

    @Autowired PostCounterService counters;
    @Autowired TransactionTemplate tx;

    private int count(long postId) {
        return jdbc.queryForObject(
                "SELECT comment_count FROM post WHERE id = ?", Integer.class, postId);
    }

    @Test
    void 댓글_수를_더하고_뺀다() {
        long author = members().member().create();
        long postId = new PostFixtures(jdbc).create(author, PostFixtures.State.PUBLISHED_PUBLIC);

        tx.executeWithoutResult(s -> counters.adjustCommentCount(postId, 1));
        tx.executeWithoutResult(s -> counters.adjustCommentCount(postId, 1));
        tx.executeWithoutResult(s -> counters.adjustCommentCount(postId, -1));

        assertThat(count(postId)).isEqualTo(1);
    }

    @Test
    void 트랜잭션_밖에서는_부를_수_없다() {
        long author = members().member().create();
        long postId = new PostFixtures(jdbc).create(author, PostFixtures.State.PUBLISHED_PUBLIC);

        assertThatThrownBy(() -> counters.adjustCommentCount(postId, 1))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> counters.adjustCommentCounts(Map.of(postId, 1)))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(count(postId)).isZero();
    }

    @Test
    void 영_아래로_가면_트랜잭션이_실패한다() {
        long author = members().member().create();
        long postId = new PostFixtures(jdbc).create(author, PostFixtures.State.PUBLISHED_PUBLIC);

        assertThatThrownBy(
                        () ->
                                tx.executeWithoutResult(
                                        s -> {
                                            counters.adjustCommentCount(postId, 1);
                                            counters.adjustCommentCount(postId, -2);
                                        }))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(count(postId)).isZero();
    }

    @Test
    void 여러_글을_SQL_한_번에() {
        long author = members().member().create();
        PostFixtures posts = new PostFixtures(jdbc);
        long a = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        long b = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        long c = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        jdbc.update("UPDATE post SET comment_count = 5");

        tx.executeWithoutResult(
                s -> {
                    try (SqlCounter.Scope scope = SqlCounter.start()) {
                        counters.adjustCommentCounts(Map.of(a, -2, b, -5, c, 0));
                        assertThat(scope.count()).isEqualTo(1);
                    }
                });

        assertThat(count(a)).isEqualTo(3);
        assertThat(count(b)).isZero();
        assertThat(count(c)).isEqualTo(5);
    }
}
