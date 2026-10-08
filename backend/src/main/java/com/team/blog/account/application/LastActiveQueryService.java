package com.team.blog.account.application;

import com.team.blog.account.domain.LastActiveBucket;
import com.team.blog.account.domain.LastActiveView;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 최근 활동 공개 조회 (FR-060, R-26, SC-009). 친구({@code ACCEPTED}) + 대상 공개 + 보는 사람 공개 + 값 있음일 때만 구간을 돌려준다 —
 * 나머지는 빈 값이고 응답에서 키를 뺀다. 정확한 시각은 밖으로 나가지 않는다. 005 블로그 머리말이 {@link #lastActiveFor}를 부른다.
 */
@Service
@Transactional(readOnly = true)
public class LastActiveQueryService {

    private static final String VISIBLE =
            """
            SELECT t.id, t.last_active_at
              FROM member t
              JOIN member v ON v.id = :viewer AND v.last_active_visible
              JOIN friendship f ON f.member_a_id = LEAST(v.id, t.id)
                               AND f.member_b_id = GREATEST(v.id, t.id)
                               AND f.status = 'ACCEPTED'
             WHERE t.id IN (:targets) AND t.last_active_visible AND t.last_active_at IS NOT NULL
               AND t.deleted_at IS NULL
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;
    private final ZoneId zone;

    public LastActiveQueryService(
            NamedParameterJdbcTemplate jdbc, Clock clock, ZoneId serviceZoneId) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.zone = serviceZoneId;
    }

    /** {@code viewerId}가 null(비회원)이거나 본인·친구 아님·비공개·값 없음이면 빈 값. 쿼리 1번. */
    public Optional<LastActiveView> lastActiveFor(Long viewerId, long targetId) {
        return Optional.ofNullable(lastActiveForMany(viewerId, List.of(targetId)).get(targetId));
    }

    /** 여러 대상(목록용) — 쿼리 1번. 보일 대상만 결과에 있다. */
    public Map<Long, LastActiveView> lastActiveForMany(Long viewerId, Collection<Long> targetIds) {
        Map<Long, LastActiveView> result = new LinkedHashMap<>();
        if (viewerId == null || targetIds == null || targetIds.isEmpty()) {
            return result;
        }
        Instant now = clock.instant();
        jdbc.query(
                VISIBLE,
                new MapSqlParameterSource("viewer", viewerId).addValue("targets", targetIds),
                rs -> {
                    long id = rs.getLong("id");
                    if (id == viewerId) {
                        return;
                    }
                    Timestamp at = rs.getTimestamp("last_active_at");
                    bucket(at == null ? null : at.toInstant(), now)
                            .ifPresent(view -> result.put(id, view));
                });
        return result;
    }

    /** 이미 읽은 값으로 구간을 만든다(친구 목록 쿼리가 함께 읽었을 때). */
    public Optional<LastActiveView> bucket(Instant lastActiveAt, Instant now) {
        return LastActiveBucket.of(lastActiveAt, now, zone);
    }

    public Instant now() {
        return clock.instant();
    }
}
