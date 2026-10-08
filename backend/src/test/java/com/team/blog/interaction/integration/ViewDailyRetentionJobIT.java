package com.team.blog.interaction.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.interaction.application.ViewDailyRetentionJob;
import com.team.blog.interaction.support.ViewFixtures;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.fixture.PostFixtures;
import java.sql.Date;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 일별 조회수 90일 보관 (009 T036, US4 #3·#4, SC-011, FR-032). 날짜를 정해 부를 때는 {@link
 * ViewDailyRetentionJob#purge(LocalDate)}를, 예약 경로는 {@link ViewDailyRetentionJob#run()}을 부른다 —
 * {@code Clock} Bean을 바꾸는 새 컨텍스트를 만들지 않으려는 것이다.
 */
class ViewDailyRetentionJobIT extends IntegrationTestBase {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 8);

    @Autowired ViewDailyRetentionJob job;

    private long postId;

    @BeforeEach
    void setUp() {
        postId =
                new PostFixtures(jdbc)
                        .create(members().member().create(), PostFixtures.State.PUBLISHED_PUBLIC);
    }

    private void daily(long post, LocalDate day, long views) {
        jdbc.update(
                "INSERT INTO post_view_daily (post_id, view_date, views) VALUES (?, ?, ?)",
                post,
                Date.valueOf(day),
                views);
    }

    private long rows() {
        return jdbc.queryForObject("SELECT count(*) FROM post_view_daily", Long.class);
    }

    @Test
    void 구십일_넘은_것만_지운다() {
        daily(postId, TODAY.minusDays(91), 3);
        daily(postId, TODAY.minusDays(90), 4);
        daily(postId, TODAY.minusDays(89), 5);
        daily(postId, TODAY, 1);

        int deleted = job.purge(TODAY);

        assertThat(deleted).isOne();
        assertThat(
                        jdbc.queryForList(
                                "SELECT view_date FROM post_view_daily ORDER BY view_date",
                                LocalDate.class))
                .as("경계 90일 전은 남긴다")
                .containsExactly(TODAY.minusDays(90), TODAY.minusDays(89), TODAY);
    }

    @Test
    void 누적_조회수는_그대로() {
        jdbc.update("UPDATE post SET view_count = 12 WHERE id = ?", postId);
        daily(postId, TODAY.minusDays(200), 12);

        job.purge(TODAY);

        assertThat(rows()).isZero();
        assertThat(new ViewFixtures(jdbc, redis).viewCount(postId)).isEqualTo(12);
    }

    @Test
    void 글을_완전_삭제하면_일별도_사라진다() {
        daily(postId, TODAY, 2);
        jdbc.update("DELETE FROM post WHERE id = ?", postId);

        assertThat(rows()).isZero();
    }

    @Test
    void 예약_경로는_오늘_기준으로_지운다() {
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
        daily(postId, today.minusDays(120), 1);
        daily(postId, today.minusDays(1), 1);

        job.run();

        assertThat(rows()).isOne();
    }
}
