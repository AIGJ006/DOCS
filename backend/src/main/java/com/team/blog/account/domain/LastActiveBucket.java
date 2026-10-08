package com.team.blog.account.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * 최근 활동 구간 (R-26, FR-060). 서비스 시간대(Asia/Seoul) 달력 날짜 차이 d로 나눈다: d ≤ 0 {@code TODAY}, 1 {@code
 * YESTERDAY}, 2~6 {@code DAYS_AGO}(days), 7 이상 {@code OVER_A_WEEK}. 정확한 시각은 밖으로 내보내지 않는다.
 */
public enum LastActiveBucket {
    TODAY,
    YESTERDAY,
    DAYS_AGO,
    OVER_A_WEEK;

    public static Optional<LastActiveView> of(Instant lastActiveAt, Instant now, ZoneId zone) {
        if (lastActiveAt == null) {
            return Optional.empty();
        }
        LocalDate then = lastActiveAt.atZone(zone).toLocalDate();
        LocalDate today = now.atZone(zone).toLocalDate();
        long d = ChronoUnit.DAYS.between(then, today);
        if (d <= 0) {
            return Optional.of(new LastActiveView(TODAY, null));
        }
        if (d == 1) {
            return Optional.of(new LastActiveView(YESTERDAY, null));
        }
        if (d <= 6) {
            return Optional.of(new LastActiveView(DAYS_AGO, (int) d));
        }
        return Optional.of(new LastActiveView(OVER_A_WEEK, null));
    }
}
