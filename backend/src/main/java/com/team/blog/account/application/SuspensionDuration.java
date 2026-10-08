package com.team.blog.account.application;

import java.time.Duration;
import java.time.Instant;

/**
 * 정지 기간 (014 data-model §3, FR-035). 요청 값은 {@code P1D}·{@code P7D}·{@code P30D}·{@code PERMANENT}.
 * 정지를 가진 account 모듈에 둔다(014 moderation이 이 enum으로 {@link SuspensionService#suspend}를 부른다).
 */
public enum SuspensionDuration {
    P1D(Duration.ofDays(1)),
    P7D(Duration.ofDays(7)),
    P30D(Duration.ofDays(30)),
    PERMANENT(null);

    private final Duration length;

    SuspensionDuration(Duration length) {
        this.length = length;
    }

    /** 종료 시각. 영구면 {@code null}. */
    public Instant endsAt(Instant startedAt) {
        return length == null ? null : startedAt.plus(length);
    }

    public boolean isPermanent() {
        return length == null;
    }
}
