package com.team.blog.account.application;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 복구 기한과 정리 대상 경계 (015 research R5·R11, FR-021a). 복구 가능과 정리 대상은 같은 시각 {@code t = withdrawnAt +
 * grace}에서 겹치지도 비지도 않는다: {@code now ≤ t}이면 복구 가능, {@code withdrawnAt < now - grace}(= {@code now >
 * t})이면 정리 대상. 시각은 DB({@code timestamptz}, 마이크로초)와 맞추려고 {@link #now()}에서 마이크로초로 자른다.
 */
@Component
public class WithdrawalPolicy {

    private final WithdrawalProperties properties;
    private final Clock clock;

    public WithdrawalPolicy(WithdrawalProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /** 지금 (마이크로초로 자름 — DB에 쓰고 다시 읽어도 같은 값). */
    public Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    /** 복구 기한 = 신청 시각 + 유예 기간. */
    public Instant deadline(Instant withdrawnAt) {
        return Objects.requireNonNull(withdrawnAt, "withdrawnAt").plus(properties.gracePeriod());
    }

    /** 복구할 수 있는가: {@code now ≤ deadline}. 기한 정각은 복구 가능이다. */
    public boolean isRestorable(Instant withdrawnAt, Instant now) {
        return !now.isAfter(deadline(withdrawnAt));
    }

    /** 30일 정리 대상인가: {@code withdrawnAt < now - grace}. 기한 정각은 대상이 아니다. */
    public boolean isPurgeTarget(Instant withdrawnAt, Instant now) {
        return withdrawnAt.isBefore(purgeCutoff(now));
    }

    /** 정리 대상 조회 SQL의 기준 시각 ({@code withdrawn_at < :cutoff}). */
    public Instant purgeCutoff(Instant now) {
        return now.minus(properties.gracePeriod());
    }

    /** 영구 정지 자동 정리 대상인가: 열린 영구 정지 시작 시각 {@code < now - suspendedPurgeAfter} (FR-008, R11). */
    public boolean isSuspendedPurgeTarget(Instant suspensionStartedAt, Instant now) {
        return suspensionStartedAt.isBefore(suspendedPurgeCutoff(now));
    }

    /** 영구 정지 자동 정리 조회 SQL의 기준 시각 ({@code started_at < :cutoff}). */
    public Instant suspendedPurgeCutoff(Instant now) {
        return now.minus(properties.suspendedPurgeAfter());
    }
}
