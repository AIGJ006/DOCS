package com.team.blog.interaction.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.team.blog.interaction.infra.FollowRepository;
import com.team.blog.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 팔로워 수 계산 성능 (010 T031, SC-007, research R5, 24 §5). 팔로워 1만 명(탈퇴 유예 50명 섞음)에서 수가 맞고 {@code
 * ix_follow_followee}를 타며 실행 10ms 이내인지 본다. 넘으면 24 §5대로 저장 카운터 도입을 검토한다.
 */
class FollowCountPerformanceIT extends IntegrationTestBase {

    private static final double BUDGET_MILLIS = 10.0;

    @Autowired FollowRepository follows;
    @Autowired JdbcClient jdbcClient;

    private long star;

    @BeforeEach
    void seed() {
        star = members().member().handle("star").create();
        jdbc.update(
                "INSERT INTO member (handle, nickname, role, status, withdrawn_at)"
                        + " SELECT 'fan' || g, '팬' || g, 'USER',"
                        + " CASE WHEN g % 200 = 0 THEN 'WITHDRAWN' ELSE 'ACTIVE' END,"
                        + " CASE WHEN g % 200 = 0 THEN now() END"
                        + " FROM generate_series(1, 10000) g");
        // 팬 1만 명이 star를 팔로우하고, star는 팬 1만 명을 팔로우한다 (팔로잉 수도 같은 조건)
        jdbc.update(
                "INSERT INTO follow (follower_id, followee_id, created_at)"
                        + " SELECT id, ?, now() - (id || ' seconds')::interval FROM member"
                        + " WHERE handle LIKE 'fan%'",
                star);
        jdbc.update(
                "INSERT INTO follow (follower_id, followee_id, created_at)"
                        + " SELECT ?, id, now() - (id || ' seconds')::interval FROM member"
                        + " WHERE handle LIKE 'fan%'",
                star);
        // 다른 회원들의 관계 — 팬마다 다른 팬 10명 (follow 12만 행 중 star 관계가 1/6이 되게)
        jdbc.update(
                "INSERT INTO follow (follower_id, followee_id)"
                        + " SELECT a.id, b.id FROM member a"
                        + " JOIN generate_series(1, 10) d ON true"
                        + " JOIN member b ON b.id = a.id + d"
                        + " WHERE a.handle LIKE 'fan%' AND b.handle LIKE 'fan%'");
        jdbc.execute("ANALYZE member");
        jdbc.execute("ANALYZE follow");
    }

    @Test
    void 팔로워_1만명_수는_9950이고_ix_follow_followee로_10ms_이내() {
        assertThat(follows.countFollowers(star)).isEqualTo(9_950);

        String sql =
                "SELECT count(*) FROM follow f WHERE f.followee_id = :target AND NOT EXISTS"
                        + " (SELECT 1 FROM member m WHERE m.id = f.follower_id"
                        + " AND m.status = 'WITHDRAWN' AND m.deleted_at IS NULL)";
        String plan = explain(sql);
        assertThat(plan).contains("ix_follow_followee");
        assertThat(executionMillis(sql))
                .as("팔로워 수 실행 시간(ms) — 넘으면 24 §5 카운터 검토")
                .isLessThan(BUDGET_MILLIS);
    }

    @Test
    void 팔로잉_수도_ix_follow_follower로() {
        assertThat(follows.countFollowing(star)).isEqualTo(9_950);

        String sql =
                "SELECT count(*) FROM follow f WHERE f.follower_id = :target AND NOT EXISTS"
                        + " (SELECT 1 FROM member m WHERE m.id = f.followee_id"
                        + " AND m.status = 'WITHDRAWN' AND m.deleted_at IS NULL)";
        assertThat(explain(sql)).contains("ix_follow_follower");
        assertThat(executionMillis(sql))
                .as("팔로잉 수 실행 시간(ms) — 넘으면 24 §5 카운터 검토")
                .isLessThan(BUDGET_MILLIS);
    }

    private String explain(String sql) {
        return jdbcClient
                .sql("EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) " + sql)
                .param("target", star)
                .query(String.class)
                .single();
    }

    /** 웜업 한 번 뒤 세 번 재서 가장 짧은 실행 시간. */
    private double executionMillis(String sql) {
        explain(sql);
        double best = Double.MAX_VALUE;
        for (int i = 0; i < 3; i++) {
            Number millis = JsonPath.read(explain(sql), "$[0]['Execution Time']");
            best = Math.min(best, millis.doubleValue());
        }
        return best;
    }
}
