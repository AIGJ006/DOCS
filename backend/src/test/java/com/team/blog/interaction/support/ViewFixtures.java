package com.team.blog.interaction.support;

import java.util.Set;
import java.util.TreeSet;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

/** 009 조회수 시험 도우미: DB 조회수·일별 합계, Redis 모음 키 (테스트 전용). */
public final class ViewFixtures {

    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;

    public ViewFixtures(JdbcTemplate jdbc, StringRedisTemplate redis) {
        this.jdbc = jdbc;
        this.redis = redis;
    }

    public long viewCount(long postId) {
        return jdbc.queryForObject("SELECT view_count FROM post WHERE id = ?", Long.class, postId);
    }

    /** 그 글의 일별 합계 총합 (행이 없으면 0). */
    public long dailyTotal(long postId) {
        return jdbc.queryForObject(
                "SELECT COALESCE(sum(views), 0) FROM post_view_daily WHERE post_id = ?",
                Long.class,
                postId);
    }

    public Set<String> keys(String pattern) {
        Set<String> keys = new TreeSet<>();
        try (Cursor<String> cursor =
                redis.scan(ScanOptions.scanOptions().match(pattern).count(1000).build())) {
            cursor.forEachRemaining(keys::add);
        }
        return keys;
    }

    /** 반영을 기다리는 묶음 ({@code view:pending:*}·{@code view:processing:*}). */
    public Set<String> waitingBatches() {
        Set<String> keys = keys("view:pending:*");
        keys.addAll(keys("view:processing:*"));
        return keys;
    }
}
