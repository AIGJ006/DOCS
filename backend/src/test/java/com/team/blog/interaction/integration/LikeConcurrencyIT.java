package com.team.blog.interaction.integration;

import static com.team.blog.interaction.support.LikeApi.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.interaction.support.LikeApi;
import com.team.blog.interaction.support.LikeEventProbe;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import com.team.blog.support.fixture.PostFixtures;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 좋아요 동시성 (009 T013, SC-001·SC-002, US1 #3·#4, research R1). 실제 PostgreSQL에서 PK 충돌 대기와 카운터 행 잠금으로
 * 1인 1글 1건·수 = 실제 건수가 지켜지는지 본다.
 */
class LikeConcurrencyIT extends IntegrationTestBase {

    @Autowired LikeEventProbe probe;

    private long postId;

    @BeforeEach
    void setUp() {
        long author = members().member().create();
        postId = new PostFixtures(jdbc).create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        probe.arm();
    }

    @AfterEach
    void tearDown() {
        probe.reset();
    }

    private LikeApi api() {
        return new LikeApi(mockMvc);
    }

    private int likeCount() {
        return jdbc.queryForObject(
                "SELECT like_count FROM post WHERE id = ?", Integer.class, postId);
    }

    private long likeRows() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM post_like WHERE post_id = ?", Long.class, postId);
    }

    private static List<Integer> runAll(List<Callable<Integer>> calls) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(calls.size());
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Integer>> futures = new ArrayList<>();
            for (Callable<Integer> call : calls) {
                futures.add(
                        pool.submit(
                                () -> {
                                    start.await();
                                    return call.call();
                                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : futures) {
                statuses.add(f.get(60, TimeUnit.SECONDS));
            }
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void 같은_회원_동시_20번은_1건_이벤트_1번() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        List<Callable<Integer>> calls = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            calls.add(() -> status(api().like(session, postId)));
        }

        List<Integer> statuses = runAll(calls);

        assertThat(statuses).containsOnly(200);
        assertThat(likeRows()).isEqualTo(1);
        assertThat(likeCount()).isEqualTo(1);
        assertThat(probe.liked()).isEqualTo(1);
    }

    @Test
    void 오십명_동시는_50() throws Exception {
        List<Cookie> sessions = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            sessions.add(TestLogin.loginAs(mockMvc, members().member().create()));
        }
        List<Callable<Integer>> calls = new ArrayList<>();
        for (Cookie s : sessions) {
            calls.add(() -> status(api().like(s, postId)));
        }

        assertThat(runAll(calls)).containsOnly(200);
        assertThat(likeRows()).isEqualTo(50);
        assertThat(likeCount()).isEqualTo(50);
        assertThat(probe.liked()).isEqualTo(50);
    }

    @Test
    void 좋아요_취소_섞기_3회_모두_수가_건수와_같다() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        long other = members().member().create();
        api().like(TestLogin.loginAs(mockMvc, other), postId);

        for (int round = 0; round < 3; round++) {
            // 좋아요·취소 합쳐 1분 60번 제한(FR-011)에 걸리지 않게 회차마다 제한 키를 비운다
            redis.delete("ratelimit:like:" + me);
            List<Callable<Integer>> calls = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                calls.add(() -> status(api().like(session, postId)));
                calls.add(() -> status(api().unlike(session, postId)));
            }
            Collections.shuffle(calls);

            assertThat(runAll(calls)).as("회차 " + round).containsOnly(200);
            assertThat(likeCount()).as("회차 " + round + " 수 = 건수").isEqualTo((int) likeRows());
            assertThat(likeRows()).as("회차 " + round).isBetween(1L, 2L);
        }
        assertThat(probe.liked() - probe.unliked()).as("실제로 바뀐 횟수만 이벤트").isEqualTo(likeRows());
    }
}
