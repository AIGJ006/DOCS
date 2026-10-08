package com.team.blog.account.application;

import java.time.Instant;

/**
 * 열린 정지 (R-23). {@code endsAt}이 null이면 영구.
 *
 * @param reason 정지 사유 (로그인 거부 문구·{@code details.reason})
 */
public record OpenSuspension(long suspensionId, String reason, Instant startedAt, Instant endsAt) {

    public boolean permanent() {
        return endsAt == null;
    }
}
