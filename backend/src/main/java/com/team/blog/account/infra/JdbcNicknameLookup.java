package com.team.blog.account.infra;

import com.team.blog.account.application.policy.NicknameLookup;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** 닉네임 중복 조회: {@code lower(nickname) = lower(?)}, 자기 자신 제외 ({@code uq_member_nickname} 인덱스). */
@Component
public class JdbcNicknameLookup implements NicknameLookup {

    private final JdbcClient jdbc;

    public JdbcNicknameLookup(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean existsIgnoreCase(String nickname, Long excludeMemberId) {
        return jdbc.sql(
                        "SELECT EXISTS (SELECT 1 FROM member WHERE lower(nickname) = lower(:nickname)"
                                + " AND (CAST(:exclude AS bigint) IS NULL OR id <> :exclude))")
                .param("nickname", nickname)
                .param("exclude", excludeMemberId)
                .query(Boolean.class)
                .single();
    }
}
