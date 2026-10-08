package com.team.blog.account.application;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** 최근 활동 시각 갱신 (account 내부용, FR-059). 간격 조절은 {@code ActiveTouchThrottle}이 한다. */
@Service
public class LastActiveService {

    private final JdbcClient jdbc;

    public LastActiveService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** {@code last_active_at = now()} (자동 커밋 한 문장). */
    public void touch(long memberId) {
        jdbc.sql("UPDATE member SET last_active_at = now() WHERE id = ? AND deleted_at IS NULL")
                .param(memberId)
                .update();
    }
}
