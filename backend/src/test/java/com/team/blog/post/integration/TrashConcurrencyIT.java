package com.team.blog.post.integration;

import static com.team.blog.post.support.TrashApi.read;
import static com.team.blog.post.support.TrashApi.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.post.application.TrashPurgeJob;
import com.team.blog.post.support.PostTestConfig;
import com.team.blog.post.support.PostTestConfig.CommittedEvents;
import com.team.blog.post.support.TrashApi;
import com.team.blog.post.support.TrashFixtures;
import com.team.blog.shared.event.PostPurged;
import com.team.blog.shared.event.PostRestored;
import com.team.blog.shared.event.PostTrashed;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import com.team.blog.support.fixture.PostFixtures;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 삭제·복구·영구 삭제·배치의 동시 실행 (006 T072, FR-036, 13 §2-4 엣지 케이스). 상태 변경은 모두 {@code SELECT … FOR UPDATE}로
 * 잠근 뒤 다시 판단하므로 어떤 순서로 겹쳐도 최종 상태는 정상·휴지통·없음 중 하나이고 500은 없다.
 */
@Import(PostTestConfig.class)
class TrashConcurrencyIT extends IntegrationTestBase {

    private static final int THREADS = 10;

    @Autowired CommittedEvents events;
    @Autowired TrashPurgeJob job;
    @Autowired TransactionTemplate tx;

    private TrashApi api() {
        return new TrashApi(mockMvc);
    }

    private PostFixtures posts() {
        return new PostFixtures(jdbc);
    }

    private TrashFixtures fixtures() {
        return new TrashFixtures(jdbc);
    }

    @BeforeEach
    void clearEvents() {
        events.clear();
    }

    private record Call(String kind, int status, String body) {}

    private static List<Call> runAll(List<Callable<Call>> calls) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(calls.size());
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Call>> futures = new ArrayList<>();
            for (Callable<Call> call : calls) {
                futures.add(
                        pool.submit(
                                () -> {
                                    start.await();
                                    return call.call();
                                }));
            }
            start.countDown();
            List<Call> results = new ArrayList<>();
            for (Future<Call> f : futures) {
                results.add(f.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private static Call call(String kind, MvcResult result) {
        return new Call(kind, status(result), TrashApi.body(result));
    }

    @Test
    void 삭제_복구_영구삭제를_10개씩_동시에_보내도_상태가_하나로_정해지고_500이_없다() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = posts().create(me, PostFixtures.State.PUBLISHED_PUBLIC);
        new TrashFixtures(jdbc).comment(postId, me, null);

        List<Callable<Call>> calls = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            calls.add(() -> call("trash", api().trash(session, postId)));
            calls.add(() -> call("restore", api().restore(session, postId)));
            calls.add(() -> call("purge", api().purge(session, postId)));
        }
        Collections.shuffle(calls);
        List<Call> results = runAll(calls);

        assertThat(results).allSatisfy(c -> assertThat(c.status()).as(c.toString()).isIn(200, 404));
        long trashed = events.of(PostTrashed.class).size();
        long restored = events.of(PostRestored.class).size();
        long purged = events.of(PostPurged.class).size();
        long restoreOk =
                results.stream()
                        .filter(c -> c.kind().equals("restore") && c.status() == 200)
                        .count();
        long purgeOk =
                results.stream().filter(c -> c.kind().equals("purge") && c.status() == 200).count();
        assertThat(restored).isEqualTo(restoreOk);
        assertThat(purged).isEqualTo(purgeOk).isLessThanOrEqualTo(1);

        boolean exists = fixtures().exists(postId);
        if (!exists) {
            assertThat(purged).isEqualTo(1);
            assertThat(trashed).isEqualTo(restored + 1);
        } else if (fixtures().deletedAt(postId) != null) {
            assertThat(purged).isZero();
            assertThat(trashed).isEqualTo(restored + 1);
        } else {
            assertThat(purged).isZero();
            assertThat(trashed).isEqualTo(restored);
        }
    }

    @Test
    void 휴지통_이동_복구와_비우기_배치가_겹쳐도_deadlock과_500이_없다() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        Instant now = Instant.now();
        List<Long> expired = new ArrayList<>();
        List<Long> normal = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            long id = posts().create(me, PostFixtures.State.PUBLISHED_PUBLIC);
            fixtures().trashedAt(id, now.minus(Duration.ofDays(31)).minusSeconds(i));
            expired.add(id);
            normal.add(posts().create(me, PostFixtures.State.PUBLISHED_PUBLIC));
        }

        List<Callable<Call>> calls = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            long restoreTarget = expired.get(i);
            long trashTarget = normal.get(i);
            calls.add(() -> call("restore", api().restore(session, restoreTarget)));
            calls.add(() -> call("trash", api().trash(session, trashTarget)));
        }
        calls.add(
                () -> {
                    TrashPurgeJob.Result r = job.purgeExpired(Instant.now(), Duration.ofMinutes(5));
                    return new Call("job", r.failed() == 0 ? 200 : 500, r.toString());
                });
        List<Call> results = runAll(calls);

        assertThat(results).allSatisfy(c -> assertThat(c.status()).as(c.toString()).isIn(200, 404));
        for (long id : expired) {
            // 복구가 먼저면 정상 글로 남고, 배치가 먼저면 없다 — 휴지통에 남아 있는 경우는 없다
            assertThat(fixtures().exists(id) && fixtures().deletedAt(id) != null)
                    .as("post " + id)
                    .isFalse();
        }
        for (long id : normal) {
            assertThat(fixtures().deletedAt(id)).as("post " + id).isNotNull();
        }
        long purged = events.of(PostPurged.class).size();
        long restored = events.of(PostRestored.class).size();
        assertThat(purged + restored).isEqualTo(THREADS);
    }

    @Test
    void 관리_목록_조회는_쓰기_잠금_중에도_막히지_않는다() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long postId = posts().create(me, PostFixtures.State.PUBLISHED_PUBLIC);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        CompletableFuture<Void> holder =
                CompletableFuture.runAsync(
                        () ->
                                tx.executeWithoutResult(
                                        s -> {
                                            jdbc.queryForObject(
                                                    "SELECT id FROM post WHERE id = ? FOR UPDATE",
                                                    Long.class,
                                                    postId);
                                            locked.countDown();
                                            try {
                                                release.await(30, TimeUnit.SECONDS);
                                            } catch (InterruptedException e) {
                                                Thread.currentThread().interrupt();
                                            }
                                        }));
        try {
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            MvcResult list =
                    CompletableFuture.supplyAsync(
                                    () -> {
                                        try {
                                            return api().list(session, "tab=published");
                                        } catch (Exception e) {
                                            throw new IllegalStateException(e);
                                        }
                                    })
                            .get(5, TimeUnit.SECONDS);
            assertThat(status(list)).isEqualTo(200);
            List<Integer> ids = read(list, "$.items[*].id");
            assertThat(ids).containsExactly((int) postId);
        } finally {
            release.countDown();
            holder.get(30, TimeUnit.SECONDS);
        }
    }
}
