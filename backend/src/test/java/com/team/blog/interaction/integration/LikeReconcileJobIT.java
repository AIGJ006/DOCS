package com.team.blog.interaction.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.interaction.application.LikeReconcileJob;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.fixture.PostFixtures;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * 좋아요 수 보정 배치 (009 T037, SC-003, FR-005, 30 §4-1). 예약 경로 {@link LikeReconcileJob#run()}을 직접 부른다.
 */
@ExtendWith(OutputCaptureExtension.class)
class LikeReconcileJobIT extends IntegrationTestBase {

    @Autowired LikeReconcileJob job;
    @Autowired LockProvider lockProvider;

    private long postA;
    private long postB;

    @BeforeEach
    void setUp() {
        long author = members().member().create();
        long reader = members().member().create();
        PostFixtures posts = new PostFixtures(jdbc);
        postA = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        postB = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        jdbc.update("INSERT INTO post_like (post_id, member_id) VALUES (?, ?)", postA, reader);
        jdbc.update("UPDATE post SET like_count = 1 WHERE id = ?", postA);
    }

    private int likeCount(long postId) {
        return jdbc.queryForObject(
                "SELECT like_count FROM post WHERE id = ?", Integer.class, postId);
    }

    @Test
    void 정상이면_고칠_것이_없고_WARN도_없다(CapturedOutput output) {
        assertThat(job.reconcile()).isEmpty();
        job.run();
        assertThat(output.getAll()).doesNotContain("좋아요 수가 실제와 달라");
    }

    @Test
    void 틀어진_좋아요_수를_고치고_글_번호를_WARN으로(CapturedOutput output) {
        jdbc.update("UPDATE post SET like_count = 7 WHERE id = ?", postA);
        jdbc.update("UPDATE post SET like_count = 2 WHERE id = ?", postB);

        job.run();

        assertThat(likeCount(postA)).isOne();
        assertThat(likeCount(postB)).isZero();
        assertThat(output.getAll())
                .contains("좋아요 수가 실제와 달라")
                .contains(String.valueOf(postA))
                .contains(String.valueOf(postB));
    }

    @Test
    void 다른_인스턴스가_잠금을_쥐고_있으면_건너뛴다() {
        jdbc.update("UPDATE post SET like_count = 9 WHERE id = ?", postA);
        Optional<SimpleLock> other =
                lockProvider.lock(
                        new LockConfiguration(
                                Instant.now(),
                                "like-reconcile",
                                Duration.ofMinutes(5),
                                Duration.ZERO));
        assertThat(other).isPresent();
        try {
            job.run();
            assertThat(likeCount(postA)).isEqualTo(9);
        } finally {
            other.get().unlock();
        }

        job.run();

        assertThat(likeCount(postA)).isOne();
    }

    @Test
    void 두_인스턴스가_동시에_돌아도_결과는_같다() {
        jdbc.update("UPDATE post SET like_count = 9 WHERE id = ?", postA);

        List<CompletableFuture<Void>> runs =
                List.of(CompletableFuture.runAsync(job::run), CompletableFuture.runAsync(job::run));
        runs.forEach(CompletableFuture::join);

        assertThat(likeCount(postA)).isOne();
    }
}
