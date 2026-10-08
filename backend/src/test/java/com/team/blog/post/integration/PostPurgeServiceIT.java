package com.team.blog.post.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team.blog.post.application.PostPurgeService;
import com.team.blog.post.support.AuthoringFixtures;
import com.team.blog.post.support.PostTestConfig;
import com.team.blog.post.support.PostTestConfig.CommittedEvents;
import com.team.blog.post.support.PurgeStepProbe;
import com.team.blog.shared.event.PostPurged;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.RedisOutage;
import com.team.blog.support.fixture.PostFixtures;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

/** 완전 삭제 공용 루틴 (006 T005, research R10·R22, contracts/events.md §2). */
@Import(PostTestConfig.class)
class PostPurgeServiceIT extends IntegrationTestBase {

    @Autowired PostPurgeService purgeService;
    @Autowired TransactionTemplate tx;
    @Autowired PurgeStepProbe probe;
    @Autowired CommittedEvents events;

    @BeforeEach
    void arm() {
        probe.arm();
        events.clear();
    }

    @AfterEach
    void disarm() {
        probe.reset();
    }

    private long count(long postId) {
        return jdbc.queryForObject("SELECT count(*) FROM post WHERE id = ?", Long.class, postId);
    }

    private long trashedPost(long authorId) {
        return new PostFixtures(jdbc).create(authorId, PostFixtures.State.TRASHED);
    }

    @Test
    void 단계는_order_오름차순으로_DELETE_전에_불리고_뒤에_행이_없다() {
        long author = members().member().create();
        long postId = trashedPost(author);

        tx.executeWithoutResult(s -> purgeService.purge(postId, author, true));

        assertThat(probe.ordersFor(postId)).containsExactly(5, 25);
        assertThat(probe.calls()).allMatch(PurgeStepProbe.Call::postRowPresent);
        assertThat(count(postId)).isZero();
    }

    @Test
    void 단계가_예외를_던지면_전체_롤백되어_행이_남는다() {
        long author = members().member().create();
        long postId = trashedPost(author);
        probe.failOn(postId);

        assertThatThrownBy(
                        () ->
                                tx.executeWithoutResult(
                                        s -> purgeService.purge(postId, author, true)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(count(postId)).isEqualTo(1);
        assertThat(events.of(PostPurged.class)).isEmpty();
    }

    @Test
    void notify면_PostPurged를_한_번_아니면_없음() {
        long author = members().member().create();
        long notified = trashedPost(author);
        long silent = trashedPost(author);

        tx.executeWithoutResult(s -> purgeService.purge(notified, author, true));
        tx.executeWithoutResult(s -> purgeService.purge(silent, author, false));

        assertThat(events.of(PostPurged.class)).containsExactly(new PostPurged(notified, author));
    }

    @Test
    void 커밋_후_자동_저장_키와_dirty를_지운다() {
        long author = members().member().create();
        long postId = trashedPost(author);
        AuthoringFixtures fixtures = new AuthoringFixtures(jdbc, redis);
        fixtures.putAutosave(postId, author, "제목", "본문", 5, Instant.now(), true);

        tx.executeWithoutResult(s -> purgeService.purge(postId, author, true));

        assertThat(redis.hasKey(AuthoringFixtures.autosaveKey(postId))).isFalse();
        assertThat(fixtures.isDirty(postId)).isFalse();
    }

    @Test
    void Redis_장애여도_DB_삭제는_커밋된다() {
        long author = members().member().create();
        long postId = trashedPost(author);

        try (RedisOutage outage = RedisOutage.start()) {
            tx.executeWithoutResult(s -> purgeService.purge(postId, author, true));
        }

        assertThat(count(postId)).isZero();
    }

    @Test
    void 트랜잭션_밖에서_부르면_예외() {
        long author = members().member().create();
        long postId = trashedPost(author);

        assertThatThrownBy(() -> purgeService.purge(postId, author, true))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(count(postId)).isEqualTo(1);
    }
}
