package com.team.blog.interaction.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.interaction.application.ViewFlushJob;
import com.team.blog.interaction.infra.RedisViewStore;
import com.team.blog.interaction.support.ViewFixtures;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.fixture.PostFixtures;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 1분 반영 (009 T026, US3 #8·US4 #1·#2, SC-007·SC-009, FR-027·028·030, contracts/view-pipeline.md §3).
 * Redis 모음 키를 직접 만들어 두고 {@link ViewFlushJob#flush()}를 부른다. 날짜는 키 이름에 실린 날짜(기록 시각의 서비스 시간대 날짜)를 쓰므로
 * 시계를 고정하지 않고 날짜가 다른 키로 자정 경계를 확인한다.
 */
class ViewFlushJobIT extends IntegrationTestBase {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 7);

    @Autowired ViewFlushJob job;
    @Autowired RedisViewStore store;

    private long a;
    private long b;

    @BeforeEach
    void setUp() {
        long author = members().member().create();
        PostFixtures posts = new PostFixtures(jdbc);
        a = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        b = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);
    }

    private ViewFixtures views() {
        return new ViewFixtures(jdbc, redis);
    }

    private void pending(LocalDate day, long postId, long n) {
        redis.opsForHash().increment(RedisViewStore.pendingKey(day), String.valueOf(postId), n);
    }

    private long daily(long postId, LocalDate day) {
        return jdbc.queryForObject(
                "SELECT COALESCE(sum(views), 0) FROM post_view_daily WHERE post_id = ? AND view_date = ?",
                Long.class,
                postId,
                Date.valueOf(day));
    }

    @Test
    void 일분_반영으로_누적과_일별이_함께() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        store.record(a, "m:1", today, java.time.Duration.ofHours(24), 1);
        store.record(a, "m:2", today, java.time.Duration.ofHours(24), 1);
        store.record(b, "m:1", today, java.time.Duration.ofHours(24), 1);

        ViewFlushJob.Result result = job.flush();

        assertThat(result.applied()).isEqualTo(2);
        assertThat(views().viewCount(a)).isEqualTo(2);
        assertThat(daily(a, today)).isEqualTo(2);
        assertThat(views().viewCount(b)).isEqualTo(1);
        assertThat(daily(b, today)).isEqualTo(1);

        store.record(b, "m:2", today, java.time.Duration.ofHours(24), 1);
        job.flush();
        assertThat(views().viewCount(b)).isEqualTo(2);
        assertThat(daily(b, today)).as("같은 날 합계에 더한다").isEqualTo(2);
    }

    @Test
    void 자정_넘긴_조회는_그_날짜에() {
        pending(DAY, a, 3);
        pending(DAY.plusDays(1), a, 2);

        job.flush();

        assertThat(daily(a, DAY)).isEqualTo(3);
        assertThat(daily(a, DAY.plusDays(1))).isEqualTo(2);
        assertThat(views().viewCount(a)).isEqualTo(5);
    }

    @Test
    void 중간에_멈춰도_다음_실행이_남은_것만_반영한다() {
        // 이전 실행이 a는 반영·HDEL까지 끝내고 b와 c만 남긴 채 멈춘 상태
        jdbc.update("UPDATE post SET view_count = 4 WHERE id = ?", a);
        jdbc.update(
                "INSERT INTO post_view_daily (post_id, view_date, views) VALUES (?, ?, 4)",
                a,
                Date.valueOf(DAY));
        long c =
                new PostFixtures(jdbc)
                        .create(members().member().create(), PostFixtures.State.PUBLISHED_PUBLIC);
        String processing = "view:processing:" + RedisViewStore.dateText(DAY) + ":crashed-run";
        redis.opsForHash()
                .putAll(processing, Map.of(String.valueOf(b), "6", String.valueOf(c), "1"));
        // 그사이 새로 모인 조회
        pending(DAY, a, 1);

        job.flush();

        assertThat(views().viewCount(a)).isEqualTo(5);
        assertThat(daily(a, DAY)).isEqualTo(5);
        assertThat(views().viewCount(b)).isEqualTo(6);
        assertThat(daily(b, DAY)).isEqualTo(6);
        assertThat(views().viewCount(c)).isEqualTo(1);
        assertThat(views().waitingBatches()).isEmpty();

        job.flush();
        assertThat(views().viewCount(b)).as("다시 돌려도 두 번 더하지 않는다").isEqualTo(6);
    }

    @Test
    void 완전_삭제된_글은_건너뛴다() {
        pending(DAY, a, 2);
        pending(DAY, b, 3);
        jdbc.update("DELETE FROM post WHERE id = ?", b);
        pending(DAY, new PostFixtures(jdbc).nonexistentId(), 9);

        ViewFlushJob.Result result = job.flush();

        assertThat(result.applied()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(2);
        assertThat(views().viewCount(a)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM post_view_daily", Long.class)).isOne();
        assertThat(views().waitingBatches()).isEmpty();
    }

    @Test
    void updated_at은_바뀌지_않는다() {
        Timestamp before =
                jdbc.queryForObject("SELECT updated_at FROM post WHERE id = ?", Timestamp.class, a);
        pending(DAY, a, 5);

        job.flush();

        assertThat(views().viewCount(a)).isEqualTo(5);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT updated_at FROM post WHERE id = ?", Timestamp.class, a))
                .isEqualTo(before);
    }

    @Test
    void 실행_후_처리_대기_묶음_0개() {
        pending(DAY, a, 1);
        pending(DAY.plusDays(1), b, 1);
        redis.opsForHash()
                .put(
                        "view:processing:" + RedisViewStore.dateText(DAY) + ":x",
                        String.valueOf(a),
                        "2");

        job.flush();

        assertThat(views().waitingBatches()).isEmpty();
        assertThat(views().viewCount(a)).isEqualTo(3);
    }
}
