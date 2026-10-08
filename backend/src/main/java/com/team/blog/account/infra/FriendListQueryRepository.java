package com.team.blog.account.infra;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 친구 목록·받은 요청 목록 (data-model §2-5, FR-055·056). 페이지마다 {@code friendship} + {@code member} JOIN SQL
 * 1번 — media의 {@code image}는 JOIN하지 않는다(프로필 사진은 호출한 쪽이 {@code ProfileImageQuery.currentKeysOf}로 1번에
 * 붙인다, constitution II). 탈퇴 유예·익명 처리된 상대는 빠진다(그 블로그는 404).
 *
 * <p>정렬: 친구 {@code accepted_at DESC, other_id DESC}, 받은 요청 {@code created_at DESC, other_id DESC}.
 * 커서 키는 {@code (시각, other_id)}.
 */
@Repository
public class FriendListQueryRepository {

    /**
     * 목록 한 줄. {@code at}은 친구가 된 시각 또는 요청 시각. {@code visibleLastActiveAt}은 친구 목록에서 상대·나 둘 다 최근 활동을
     * 공개할 때만 값이 있다(받은 요청 목록은 항상 null) — 같은 쿼리에서 읽어 SQL 수를 늘리지 않는다.
     */
    public record Row(
            long otherId,
            String handle,
            String nickname,
            Instant at,
            Instant visibleLastActiveAt) {}

    private static final String FRIENDS =
            """
            SELECT f.other_id, m.handle, m.nickname, f.at,
                   CASE WHEN m.last_active_visible
                         AND (SELECT me.last_active_visible FROM member me WHERE me.id = :me)
                        THEN m.last_active_at END AS visible_last_active_at
              FROM (
              SELECT member_b_id AS other_id, accepted_at AS at FROM friendship
               WHERE member_a_id = :me AND status = 'ACCEPTED'
              UNION ALL
              SELECT member_a_id AS other_id, accepted_at AS at FROM friendship
               WHERE member_b_id = :me AND status = 'ACCEPTED'
            ) f
            JOIN member m ON m.id = f.other_id
            WHERE m.status <> 'WITHDRAWN' AND m.deleted_at IS NULL
            """;

    private static final String REQUESTS =
            """
            SELECT f.other_id, m.handle, m.nickname, f.at,
                   CAST(NULL AS timestamptz) AS visible_last_active_at
              FROM (
              SELECT member_b_id AS other_id, created_at AS at FROM friendship
               WHERE member_a_id = :me AND status = 'PENDING' AND requested_by <> :me
              UNION ALL
              SELECT member_a_id AS other_id, created_at AS at FROM friendship
               WHERE member_b_id = :me AND status = 'PENDING' AND requested_by <> :me
            ) f
            JOIN member m ON m.id = f.other_id
            WHERE m.status <> 'WITHDRAWN' AND m.deleted_at IS NULL
            """;

    private static final String CURSOR = " AND (f.at, f.other_id) < (:at, :otherId)";
    private static final String ORDER = " ORDER BY f.at DESC, f.other_id DESC LIMIT :limit";

    private final JdbcClient jdbc;

    public FriendListQueryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** 내 친구. {@code after}가 있으면 그 키 다음부터. {@code limit}개까지. */
    public List<Row> friends(long me, Instant afterAt, Long afterOtherId, int limit) {
        return page(FRIENDS, me, afterAt, afterOtherId, limit);
    }

    /** 내가 받은 요청(PENDING, 상대가 요청). */
    public List<Row> receivedRequests(long me, Instant afterAt, Long afterOtherId, int limit) {
        return page(REQUESTS, me, afterAt, afterOtherId, limit);
    }

    private List<Row> page(String base, long me, Instant afterAt, Long afterOtherId, int limit) {
        boolean cursor = afterAt != null && afterOtherId != null;
        JdbcClient.StatementSpec spec =
                jdbc.sql(base + (cursor ? CURSOR : "") + ORDER)
                        .param("me", me)
                        .param("limit", limit);
        if (cursor) {
            spec =
                    spec.param(
                                    "at",
                                    afterAt.truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC))
                            .param("otherId", afterOtherId);
        }
        return spec.query(
                        (rs, n) -> {
                            Timestamp lastActive = rs.getTimestamp("visible_last_active_at");
                            return new Row(
                                    rs.getLong("other_id"),
                                    rs.getString("handle"),
                                    rs.getString("nickname"),
                                    rs.getTimestamp("at").toInstant(),
                                    lastActive == null ? null : lastActive.toInstant());
                        })
                .list();
    }
}
