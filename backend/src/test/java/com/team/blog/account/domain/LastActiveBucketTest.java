package com.team.blog.account.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 최근 활동 구간 (R-26, FR-060). Asia/Seoul 달력 날짜 차이로 나눈다. */
class LastActiveBucketTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @ParameterizedTest(name = "[{index}] {0} / now {1} → {2} {3}")
    @CsvSource(
            delimiter = '|',
            nullValues = "-",
            value = {
                // 같은 KST 날짜
                "2026-10-08T00:01:00+09:00 | 2026-10-08T23:59:00+09:00 | TODAY | -",
                // KST 자정 경계: 23:59 → 다음 날 00:01 = 어제
                "2026-10-07T23:59:00+09:00 | 2026-10-08T00:01:00+09:00 | YESTERDAY | -",
                "2026-10-06T12:00:00+09:00 | 2026-10-08T12:00:00+09:00 | DAYS_AGO | 2",
                "2026-10-02T12:00:00+09:00 | 2026-10-08T12:00:00+09:00 | DAYS_AGO | 6",
                "2026-10-01T23:59:00+09:00 | 2026-10-08T00:00:00+09:00 | OVER_A_WEEK | -",
                "2025-01-01T00:00:00+09:00 | 2026-10-08T00:00:00+09:00 | OVER_A_WEEK | -",
                // UTC로는 같은 날(10/07)이지만 KST로는 다른 날: 10/07 14:00Z = 10/07 23:00 KST, 10/07 16:00Z =
                // 10/08 01:00 KST
                "2026-10-07T14:00:00Z | 2026-10-07T16:00:00Z | YESTERDAY | -",
                // UTC로는 다른 날이지만 KST로는 같은 날: 10/07 16:00Z = 10/08 01:00 KST, 10/08 01:00Z = 10/08
                // 10:00 KST
                "2026-10-07T16:00:00Z | 2026-10-08T01:00:00Z | TODAY | -",
                // 시계가 어긋나 미래 값이면 오늘
                "2026-10-08T13:00:00+09:00 | 2026-10-08T12:00:00+09:00 | TODAY | -",
            })
    void buckets(String lastActive, String now, LastActiveBucket bucket, Integer days) {
        LastActiveView view =
                LastActiveBucket.of(
                                java.time.OffsetDateTime.parse(lastActive).toInstant(),
                                java.time.OffsetDateTime.parse(now).toInstant(),
                                SEOUL)
                        .orElseThrow();
        assertThat(view.bucket()).isEqualTo(bucket);
        assertThat(view.days()).isEqualTo(days);
    }

    @Test
    @DisplayName("값이 없으면 빈 값")
    void empty() {
        assertThat(LastActiveBucket.of(null, Instant.now(), SEOUL)).isEmpty();
    }
}
