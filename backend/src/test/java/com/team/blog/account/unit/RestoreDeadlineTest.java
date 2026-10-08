package com.team.blog.account.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.account.application.WithdrawalPolicy;
import com.team.blog.account.application.WithdrawalProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 복구 기한·정리 대상 경계 (015 T005, research R5, FR-021a). */
class RestoreDeadlineTest {

    private static final Instant WITHDRAWN_AT = Instant.parse("2026-10-08T06:20:00.123456Z");
    private static final Instant T = WITHDRAWN_AT.plus(Duration.ofDays(30));

    private final WithdrawalPolicy policy =
            new WithdrawalPolicy(properties(), Clock.fixed(T, ZoneOffset.UTC));

    static WithdrawalProperties properties() {
        return new WithdrawalProperties(
                Duration.ofDays(30),
                Duration.ofDays(365),
                "탈퇴",
                new WithdrawalProperties.Purge("0 0 3 * * *", 100, List.of(), List.of()));
    }

    @Test
    void 기한은_신청_시각_더하기_30일() {
        assertThat(policy.deadline(WITHDRAWN_AT)).isEqualTo(T);
    }

    @Test
    void 기한_1ms_전은_복구_가능이고_정리_대상_아님() {
        Instant now = T.minusMillis(1);
        assertThat(policy.isRestorable(WITHDRAWN_AT, now)).isTrue();
        assertThat(policy.isPurgeTarget(WITHDRAWN_AT, now)).isFalse();
    }

    @Test
    void 기한_정각은_복구_가능이고_정리_대상_아님() {
        assertThat(policy.isRestorable(WITHDRAWN_AT, T)).isTrue();
        assertThat(policy.isPurgeTarget(WITHDRAWN_AT, T)).isFalse();
    }

    @Test
    void 기한_1ms_뒤는_복구_불가이고_정리_대상() {
        Instant now = T.plusMillis(1);
        assertThat(policy.isRestorable(WITHDRAWN_AT, now)).isFalse();
        assertThat(policy.isPurgeTarget(WITHDRAWN_AT, now)).isTrue();
    }

    @Test
    void 어느_시각이든_둘_중_정확히_하나() {
        for (long ms = -5; ms <= 5; ms++) {
            Instant now = T.plusMillis(ms);
            assertThat(policy.isRestorable(WITHDRAWN_AT, now))
                    .as("t%+dms", ms)
                    .isNotEqualTo(policy.isPurgeTarget(WITHDRAWN_AT, now));
        }
    }

    @Test
    void 영구_정지_1년_경계() {
        Instant now = Instant.parse("2027-10-08T00:00:00Z");
        Instant cutoff = now.minus(Duration.ofDays(365));
        assertThat(policy.isSuspendedPurgeTarget(cutoff.minusMillis(1), now)).isTrue();
        assertThat(policy.isSuspendedPurgeTarget(cutoff, now)).isFalse();
        assertThat(policy.isSuspendedPurgeTarget(cutoff.plusSeconds(86400), now)).isFalse();
    }

    @Test
    void 지금은_마이크로초로_자른다() {
        WithdrawalPolicy p =
                new WithdrawalPolicy(
                        properties(),
                        Clock.fixed(
                                Instant.parse("2026-10-08T00:00:00.123456789Z"), ZoneOffset.UTC));
        assertThat(p.now()).isEqualTo(Instant.parse("2026-10-08T00:00:00.123456Z"));
    }
}
