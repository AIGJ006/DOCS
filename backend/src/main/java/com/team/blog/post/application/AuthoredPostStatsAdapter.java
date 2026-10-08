package com.team.blog.post.application;

import com.team.blog.account.application.port.AuthoredPostStats;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 015 탈퇴 안내의 글 수·받은 좋아요 합 (015 T021, research R7). 휴지통·임시·숨김 글도 센다 — 30일 뒤 모두 지워진다. */
@Component
public class AuthoredPostStatsAdapter implements AuthoredPostStats {

    private final JdbcClient jdbc;

    public AuthoredPostStatsAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public PostStats statsOf(long memberId) {
        return jdbc.sql(
                        "SELECT count(*) AS posts, coalesce(sum(like_count), 0) AS likes"
                                + " FROM post WHERE author_id = :m")
                .param("m", memberId)
                .query((rs, n) -> new PostStats(rs.getLong("posts"), rs.getLong("likes")))
                .single();
    }
}
