package com.team.blog.interaction.integration;

import static com.team.blog.interaction.support.FollowApi.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.interaction.application.FollowService;
import com.team.blog.interaction.support.FollowApi;
import com.team.blog.interaction.support.FollowEventProbe;
import com.team.blog.interaction.support.FollowFixtures;
import com.team.blog.shared.event.MemberFollowed;
import com.team.blog.shared.event.MemberUnfollowed;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
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
 * 팔로우 동시성 (010 T014, SC-001, US1 #3, research R4). 실제 PostgreSQL에서 PK 충돌 대기와 {@code RETURNING}으로
 * 관계가 언제나 하나이고 이벤트가 실제로 바뀐 횟수만큼만 나가는지 본다.
 */
class FollowConcurrencyIT extends IntegrationTestBase {

    @Autowired FollowEventProbe probe;

    private long target;
    private String handle;

    @BeforeEach
    void setUp() {
        target = members().member().handle("popular").create();
        handle = "popular";
        probe.arm();
    }

    @AfterEach
    void tearDown() {
        probe.reset();
    }

    private FollowApi api() {
        return new FollowApi(mockMvc);
    }

    private FollowFixtures fixtures() {
        return new FollowFixtures(jdbc);
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
    void US1_3_동시_20번_관계_1개() throws Exception {
        long me = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, me);
        List<Callable<Integer>> calls = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            calls.add(() -> status(api().follow(session, handle)));
        }

        assertThat(runAll(calls)).containsOnly(200);
        assertThat(fixtures().rows(me, target)).isEqualTo(1);
        assertThat(probe.followed()).isEqualTo(1);
        assertThat(probe.unfollowed()).isZero();
    }

    /** 팔로우·언팔로우 섞어 50번 × 3회. 요청 제한(회원당 1분 30번)에 걸리지 않게 두 회원이 25번씩 같은 대상에 동시에 보내고 회차마다 제한 키를 비운다. */
    @Test
    void 팔로우_언팔로우_섞어_50번_3회_상태와_행수와_이벤트가_맞는다() throws Exception {
        long a = members().member().create();
        long b = members().member().create();
        Cookie sa = TestLogin.loginAs(mockMvc, a);
        Cookie sb = TestLogin.loginAs(mockMvc, b);

        for (int round = 0; round < 3; round++) {
            redis.delete(FollowService.RATE_LIMIT_PREFIX + a);
            redis.delete(FollowService.RATE_LIMIT_PREFIX + b);
            List<Callable<Integer>> calls = new ArrayList<>();
            for (int i = 0; i < 25; i++) {
                Cookie s = i % 2 == 0 ? sa : sb;
                Cookie t = i % 2 == 0 ? sb : sa;
                calls.add(
                        i % 3 == 0
                                ? () -> status(api().unfollow(s, handle))
                                : () -> status(api().follow(s, handle)));
                calls.add(
                        i % 3 == 1
                                ? () -> status(api().unfollow(t, handle))
                                : () -> status(api().follow(t, handle)));
            }
            Collections.shuffle(calls);

            assertThat(runAll(calls)).as("회차 " + round).containsOnly(200);
            for (long m : new long[] {a, b}) {
                long rows = fixtures().rows(m, target);
                assertThat(rows).as("회차 " + round + " 관계 수").isBetween(0L, 1L);
                assertThat(netEvents(m)).as("회차 " + round + " 실제로 바뀐 만큼만 이벤트").isEqualTo(rows);
            }
            // 마지막 상태는 요청 하나로 확인한다: 응답의 수 = 실제 행 수
            redis.delete(FollowService.RATE_LIMIT_PREFIX + a);
            long expected = fixtures().rows(b, target) + 1;
            assertThat(
                            ((Number) FollowApi.read(api().follow(sa, handle), "$.followerCount"))
                                    .longValue())
                    .isEqualTo(expected);
        }
    }

    /** 이 회원의 팔로우 이벤트 수 − 언팔로우 이벤트 수. */
    private long netEvents(long member) {
        long followed =
                probe.events().stream()
                        .filter(e -> e instanceof MemberFollowed f && f.followerId() == member)
                        .count();
        long unfollowed =
                probe.events().stream()
                        .filter(e -> e instanceof MemberUnfollowed u && u.followerId() == member)
                        .count();
        return followed - unfollowed;
    }
}
