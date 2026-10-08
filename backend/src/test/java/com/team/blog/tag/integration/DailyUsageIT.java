package com.team.blog.tag.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team.blog.support.IntegrationTestBase;
import com.team.blog.tag.application.suggest.AiDailyLimitException;
import com.team.blog.tag.application.suggest.DailyUsage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 회원 하루 횟수 (013 T016, contracts/providers.md §8). 시각은 메서드에 넘긴다(MutableClock 대신). */
class DailyUsageIT extends IntegrationTestBase {

    /** 2026-10-08 12:00 KST. */
    private static final Instant NOON = Instant.parse("2026-10-08T03:00:00Z");

    @Autowired private DailyUsage usage;

    @Test
    void 동시_25개_중_20개만_자리를_잡는다() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(25);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < 25; i++) {
                Callable<Boolean> task =
                        () -> {
                            start.await();
                            try {
                                usage.reserve(7L, NOON);
                                return true;
                            } catch (AiDailyLimitException e) {
                                return false;
                            }
                        };
                futures.add(pool.submit(task));
            }
            start.countDown();
            int ok = 0;
            for (Future<Boolean> f : futures) {
                ok += f.get() ? 1 : 0;
            }
            assertThat(ok).isEqualTo(20);
        } finally {
            pool.shutdownNow();
        }
        assertThat(redis.opsForValue().get("ai:tag:usage:7:20261008")).isEqualTo("20");
        assertThat(usage.remaining(7L, NOON)).isZero();
    }

    @Test
    void 되돌리면_다시_잡을_수_있다() {
        for (int i = 0; i < 20; i++) {
            usage.reserve(8L, NOON);
        }
        assertThatThrownBy(() -> usage.reserve(8L, NOON))
                .isInstanceOfSatisfying(
                        AiDailyLimitException.class,
                        e -> {
                            assertThat(e.details())
                                    .isEqualTo(Map.of("resetAt", "2026-10-08T15:00:00Z"));
                            assertThat(e.headers())
                                    .containsEntry("Retry-After", String.valueOf(12 * 3600));
                        });
        usage.release(8L, NOON);
        assertThat(usage.remaining(8L, NOON)).isEqualTo(1);
        assertThat(usage.reserve(8L, NOON)).isEqualTo(20);
    }

    @Test
    void 한국_시간_0시에_새_키() {
        Instant before = Instant.parse("2026-10-08T14:59:59Z"); // 23:59:59 KST
        Instant after = Instant.parse("2026-10-08T15:00:00Z"); // 다음 날 00:00 KST
        usage.reserve(9L, before);
        usage.reserve(9L, after);
        assertThat(redis.opsForValue().get("ai:tag:usage:9:20261008")).isEqualTo("1");
        assertThat(redis.opsForValue().get("ai:tag:usage:9:20261009")).isEqualTo("1");
        assertThat(usage.resetAt(before)).isEqualTo(after);
    }

    @Test
    void TTL은_2일_남은_수는_한도에서_뺀_값() {
        assertThat(usage.remaining(10L, NOON)).isEqualTo(20);
        usage.reserve(10L, NOON);
        usage.reserve(10L, NOON);
        assertThat(usage.remaining(10L, NOON)).isEqualTo(18);
        Long ttl = redis.getExpire("ai:tag:usage:10:20261008");
        assertThat(ttl).isBetween(2 * 86400L - 5, 2 * 86400L);
    }
}
