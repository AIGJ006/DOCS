package com.team.blog.discovery.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.discovery.application.trending.TrendingSnapshotJob;
import com.team.blog.discovery.infra.TrendingRepository;
import com.team.blog.discovery.support.SearchFixtures;
import com.team.blog.discovery.support.TrendingRedisHelper;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.fixture.PostFixtures.State;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 트렌딩 계산·스냅샷 (012 T023, US2 #1~#4·#7, FR-003~009·FR-015, SC-003). */
class TrendingSnapshotIT extends IntegrationTestBase {

    @Autowired TrendingRepository repository;
    @Autowired TrendingSnapshotJob job;
    @Autowired LockProvider lockProvider;

    private long author;
    private long b;
    private long c;
    private long d;
    private Instant now;

    @BeforeEach
    void setUp() {
        author = members().member().handle("trend_a").create();
        b = members().member().create();
        c = members().member().create();
        d = members().member().create();
        now = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    private SearchFixtures fx() {
        return new SearchFixtures(jdbc);
    }

    private TrendingRedisHelper helper() {
        return new TrendingRedisHelper(redis);
    }

    private SearchFixtures.Builder post(long by, Duration age) {
        return fx().post(by).firstPublicAt(now.minus(age));
    }

    @Test
    void US2_1_점수순이고_같은_반응이면_새_글이_위() {
        long day = post(author, Duration.ofDays(1)).likes(5).create();
        long hour = post(b, Duration.ofHours(1)).likes(5).create();
        long many = post(c, Duration.ofDays(1)).likes(50).create();
        long commented = post(d, Duration.ofHours(3)).commenters(author, b, c).create();

        List<Long> ids = repository.compute(now, 100);

        // 50×3/26^1.5≈1.13, 5×3/3^1.5≈2.89, 3×2/5^1.5≈0.54, 5×3/26^1.5≈0.11
        assertThat(ids).containsExactly(hour, many, commented, day);
    }

    @Test
    void US2_2_자기_댓글만_있는_글과_조회만_많은_글은_제외() {
        long selfOnly = post(author, Duration.ofHours(1)).create();
        for (int i = 0; i < 20; i++) {
            fx().comment(selfOnly, author);
        }
        post(b, Duration.ofHours(1)).views(10_000).create();
        long liked = post(c, Duration.ofHours(1)).likes(1).create();

        assertThat(repository.compute(now, 100)).containsExactly(liked);
    }

    @Test
    void US2_3_작성자당_3개까지() {
        long p1 = post(author, Duration.ofHours(1)).likes(50).create();
        long p2 = post(author, Duration.ofHours(1)).likes(40).create();
        long p3 = post(author, Duration.ofHours(1)).likes(30).create();
        post(author, Duration.ofHours(1)).likes(20).create();
        post(author, Duration.ofHours(1)).likes(10).create();
        long other = post(b, Duration.ofHours(1)).likes(1).create();

        assertThat(repository.compute(now, 100)).containsExactly(p1, p2, p3, other);
    }

    @Test
    void US2_4_7일_경계() {
        post(author, Duration.ofDays(8)).likes(100).create();
        long edge = post(b, Duration.ofDays(7)).likes(100).create();
        long inside = post(c, Duration.ofDays(7).minusSeconds(1)).likes(1).create();

        assertThat(repository.compute(now, 100)).containsExactly(inside);
        // 시각을 1초 앞으로 돌리면 7일 경계 글도 들어온다
        assertThat(repository.compute(now.minusSeconds(1), 100)).contains(edge, inside);
    }

    @Test
    void FR005_숨긴_댓글_삭제된_자리는_빼고_같은_사람은_1명() {
        long hiddenOnly = post(author, Duration.ofHours(2)).create();
        fx().hiddenComment(hiddenOnly, b);
        fx().deletedComment(hiddenOnly, c);
        long twiceSame = post(b, Duration.ofHours(2)).create();
        fx().comment(twiceSame, c);
        fx().comment(twiceSame, c);
        fx().comment(twiceSame, c);
        long twoPeople = post(c, Duration.ofHours(2)).create();
        fx().comment(twoPeople, b);
        fx().comment(twoPeople, d);

        // 숨김·삭제만 있는 글은 반응 없음. 같은 사람 3번(1명) < 두 사람(2명)
        assertThat(repository.compute(now, 100)).containsExactly(twoPeople, twiceSame);
    }

    @Test
    void SC003_비공개_휴지통_숨김_유예작성자_글은_0() {
        for (State state :
                List.of(State.PUBLISHED_PRIVATE, State.DRAFT, State.TRASHED, State.HIDDEN)) {
            post(author, Duration.ofHours(1)).state(state).likes(10).create();
        }
        post(b, Duration.ofHours(1)).state(State.AUTHOR_WITHDRAWN).likes(10).create();
        long visible = post(c, Duration.ofHours(1)).likes(1).create();

        assertThat(repository.compute(now, 100)).containsExactly(visible);
    }

    @Test
    void 스냅샷은_TTL_1800초로_쓰고_current를_바꾼다() {
        long p1 = post(author, Duration.ofHours(1)).likes(2).create();
        long p2 = post(b, Duration.ofHours(1)).likes(1).create();

        long started = System.nanoTime();
        String id = job.refresh(now);
        long millis = (System.nanoTime() - started) / 1_000_000;

        assertThat(id).matches("\\d{12}");
        assertThat(helper().current()).isEqualTo(id);
        assertThat(helper().ids(id)).containsExactly(p1, p2);
        assertThat(helper().count(id)).isEqualTo("2");
        assertThat(helper().ttlSeconds(TrendingRedisHelper.listKey(id))).isBetween(1790L, 1800L);
        assertThat(helper().ttlSeconds(TrendingRedisHelper.countKey(id))).isBetween(1790L, 1800L);
        assertThat(helper().ttlSeconds(TrendingRedisHelper.CURRENT)).isEqualTo(-1);
        // 계산 시간 기록 (T046 판단 재료). 몇 건 수준이라 1초를 넘지 않는다
        assertThat(millis).isLessThan(1000);
    }

    @Test
    void US2_7_반응이_없으면_count_0을_쓰고_current를_바꾼다() {
        post(author, Duration.ofHours(1)).create();
        helper().write("202001010000", List.of(1L));

        String id = job.refresh(now);

        assertThat(helper().current()).isEqualTo(id);
        assertThat(helper().count(id)).isEqualTo("0");
        assertThat(helper().ids(id)).isEmpty();
    }

    @Test
    void 잠금_이름은_trendingSnapshot이고_잠겨_있으면_건너뛴다() {
        post(author, Duration.ofHours(1)).likes(1).create();
        Optional<SimpleLock> lock =
                lockProvider.lock(
                        new LockConfiguration(
                                Instant.now(),
                                TrendingSnapshotJob.LOCK_NAME,
                                Duration.ofMinutes(1),
                                Duration.ZERO));
        assertThat(lock).isPresent();
        try {
            job.run();
            assertThat(helper().current()).isNull();
        } finally {
            lock.get().unlock();
        }
        job.run();
        assertThat(helper().current()).isNotNull();
    }
}
