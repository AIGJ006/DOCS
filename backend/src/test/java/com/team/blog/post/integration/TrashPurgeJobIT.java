package com.team.blog.post.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.post.application.TrashPurgeJob;
import com.team.blog.post.support.PostTestConfig;
import com.team.blog.post.support.PostTestConfig.CommittedEvents;
import com.team.blog.post.support.PurgeStepProbe;
import com.team.blog.post.support.TrashFixtures;
import com.team.blog.shared.event.PostPurged;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.fixture.PostFixtures;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;

/**
 * 휴지통 비우기 배치 (006 T054, US4-3, SC-003, contracts/events.md §3, research R13). 시각을 정해 부를 때는 {@link
 * TrashPurgeJob#purgeExpired(Instant, Duration)}를, 예약 실행 경로(ShedLock 포함)는 {@link
 * TrashPurgeJob#run()}을 직접 부른다 — {@code Clock} Bean을 바꾸는 새 컨텍스트를 만들지 않으려는 것이다.
 */
@Import(PostTestConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class TrashPurgeJobIT extends IntegrationTestBase {

    private static final Instant NOW = Instant.parse("2026-10-08T18:30:00Z");
    private static final Duration LONG = Duration.ofMinutes(30);

    @Autowired TrashPurgeJob job;
    @Autowired PurgeStepProbe probe;
    @Autowired CommittedEvents events;

    private long author;

    private PostFixtures posts() {
        return new PostFixtures(jdbc);
    }

    private TrashFixtures fixtures() {
        return new TrashFixtures(jdbc);
    }

    @BeforeEach
    void setUp() {
        events.clear();
        probe.arm();
        author = members().member().create();
    }

    @AfterEach
    void tearDown() {
        probe.reset();
    }

    private long trashedAt(Instant deletedAt) {
        long id = posts().create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        fixtures().trashedAt(id, deletedAt);
        return id;
    }

    /** {@code deleted_at}이 31일 전인 임시글 {@code n}개를 한 번에 넣는다. */
    private List<Long> expiredDrafts(int n, Instant now) {
        return jdbc.queryForList(
                "INSERT INTO post (author_id, title, content_md, deleted_at)"
                        + " SELECT ?, '글 ' || g, '본문', CAST(? AS timestamptz) - g * interval '1 second'"
                        + " FROM generate_series(1, CAST(? AS integer)) g RETURNING id",
                Long.class,
                author,
                Timestamp.from(now.minus(Duration.ofDays(31))),
                n);
    }

    private long remaining() {
        return jdbc.queryForObject("SELECT count(*) FROM post", Long.class);
    }

    @Test
    void US4_3_30일_지난글만_삭제() {
        long expired = trashedAt(NOW.minus(Duration.ofDays(31)));
        long justExpired = trashedAt(NOW.minus(Duration.ofDays(30)).minusSeconds(1));
        long recent = trashedAt(NOW.minus(Duration.ofDays(29)));
        long boundary = trashedAt(NOW.minus(Duration.ofDays(30)));
        long normal = posts().create(author, PostFixtures.State.PUBLISHED_PUBLIC);

        TrashPurgeJob.Result result = job.purgeExpired(NOW, LONG);

        assertThat(fixtures().exists(expired)).isFalse();
        assertThat(fixtures().exists(justExpired)).isFalse();
        assertThat(fixtures().exists(recent)).isTrue();
        assertThat(fixtures().exists(boundary)).isTrue();
        assertThat(fixtures().exists(normal)).isTrue();
        assertThat(result.purged()).isEqualTo(2);
        assertThat(result.skipped()).isZero();
        assertThat(result.failed()).isZero();
        assertThat(events.of(PostPurged.class))
                .containsExactlyInAnyOrder(
                        new PostPurged(expired, author), new PostPurged(justExpired, author));
    }

    @Test
    void 묶음_250개를_한_번에_100_100_50으로_모두_처리() {
        List<Long> ids = expiredDrafts(250, NOW);

        TrashPurgeJob.Result result = job.purgeExpired(NOW, LONG);

        assertThat(result.purged()).isEqualTo(250);
        assertThat(result.batches()).isEqualTo(3);
        assertThat(remaining()).isZero();
        assertThat(events.of(PostPurged.class)).hasSize(ids.size());
    }

    @Test
    void 대상_조회_뒤_처리_전에_복구된_글은_남는다() throws Exception {
        long first = trashedAt(NOW.minus(Duration.ofDays(40)));
        long restoredMeanwhile = trashedAt(NOW.minus(Duration.ofDays(35)));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        probe.blockNext(entered, release);

        CompletableFuture<TrashPurgeJob.Result> running =
                CompletableFuture.supplyAsync(() -> job.purgeExpired(NOW, LONG));
        assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
        fixtures().trashedAt(restoredMeanwhile, null);
        release.countDown();
        TrashPurgeJob.Result result = running.get(30, TimeUnit.SECONDS);

        assertThat(fixtures().exists(first)).isFalse();
        assertThat(fixtures().exists(restoredMeanwhile)).isTrue();
        assertThat(fixtures().deletedAt(restoredMeanwhile)).isNull();
        assertThat(result.purged()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
    }

    @Test
    void 한_글이_실패해도_나머지는_지우고_실패_글은_남는다() {
        List<Long> ids = expiredDrafts(5, NOW);
        long failing = ids.get(2);
        probe.failOn(failing);

        TrashPurgeJob.Result result = job.purgeExpired(NOW, LONG);

        assertThat(result.purged()).isEqualTo(4);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(fixtures().exists(failing)).isTrue();
        assertThat(fixtures().deletedAt(failing)).isNotNull();
        assertThat(remaining()).isEqualTo(1);
        assertThat(events.of(PostPurged.class)).hasSize(4).noneMatch(e -> e.postId() == failing);
    }

    @Test
    void 실패한_글은_같은_실행에서_다시_고르지_않는다() {
        List<Long> ids = expiredDrafts(150, NOW);
        long failing = ids.get(149); // deleted_at이 가장 이른 글 — 매 묶음 맨 앞에 다시 올 수 있다
        probe.failOn(failing);

        TrashPurgeJob.Result result = job.purgeExpired(NOW, LONG);

        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.purged()).isEqualTo(149);
        assertThat(probe.ordersFor(failing)).containsExactly(5, 25);
        assertThat(remaining()).isEqualTo(1);
    }

    @Test
    void 최대_실행_시간에_이르면_다음_묶음을_시작하지_않는다() {
        expiredDrafts(250, NOW);

        TrashPurgeJob.Result result = job.purgeExpired(NOW, Duration.ZERO);

        assertThat(result.batches()).isEqualTo(1);
        assertThat(result.purged()).isEqualTo(100);
        assertThat(result.timedOut()).isTrue();
        assertThat(remaining()).isEqualTo(150);
    }

    @Test
    void 두_스레드에서_동시에_run해도_ShedLock으로_한_번만_처리() throws Exception {
        Instant now = Instant.now();
        List<Long> ids = expiredDrafts(3, now);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        probe.blockNext(entered, release);

        CompletableFuture<Void> first = CompletableFuture.runAsync(job::run);
        assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<Void> second = CompletableFuture.runAsync(job::run);
        second.get(10, TimeUnit.SECONDS); // 잠금을 못 얻어 바로 끝난다
        assertThat(remaining()).isEqualTo(3);
        release.countDown();
        first.get(30, TimeUnit.SECONDS);

        assertThat(remaining()).isZero();
        for (long id : ids) {
            assertThat(probe.ordersFor(id)).as("post " + id).containsExactly(5, 25);
        }
        assertThat(events.of(PostPurged.class)).hasSize(3);
    }

    @Test
    void 실행_결과를_INFO_로그로_남긴다(CapturedOutput output) {
        expiredDrafts(2, NOW);
        long failing = expiredDrafts(1, NOW.minusSeconds(10)).get(0);
        probe.failOn(failing);

        job.purgeExpired(NOW, LONG);

        assertThat(output.getOut())
                .containsPattern(
                        "INFO .*휴지통 비우기 완료: purged=2 skipped=0 failed=1 batches=1"
                                + " elapsedMs=\\d+ timedOut=false")
                .containsPattern("ERROR .*휴지통 비우기 실패: postId=" + failing);
    }
}
