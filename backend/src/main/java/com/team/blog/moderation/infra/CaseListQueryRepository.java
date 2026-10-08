package com.team.blog.moderation.infra;

import com.team.blog.moderation.domain.ReportReason;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 관리자 사건 목록 SQL (014 T034, contracts/moderation-sql.md §2). 대기 탭은 신고 수 많은 순 → 최근 신고 순 → 사건 번호 순,
 * 처리됨 탭은 처리 시각 최근 순. 한 페이지 {@code size + 1}개를 읽어 다음 페이지가 있는지 본다.
 */
@Repository
public class CaseListQueryRepository {

    private final JdbcClient jdbc;

    public CaseListQueryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 대기 사건 (고아 사건은 빼고 — 배치가 닫는다).
     *
     * @param after 앞 페이지 마지막 항목의 {@code [count, lastReportedAt, id]} (첫 페이지면 {@code null})
     */
    public List<PendingRow> pending(PendingKey after, int limit) {
        String where =
                after == null ? "" : " WHERE (cnt, last_reported_at, id) < (:cnt, :last, :id)";
        JdbcClient.StatementSpec spec =
                jdbc.sql(
                        """
                        WITH c AS (
                          SELECT rc.id, rc.target_type, rc.snapshot_title, rc.snapshot_content,
                                 rc.target_author_id, count(r.id) AS cnt, max(r.created_at) AS last_reported_at
                            FROM report_case rc
                            JOIN report r ON r.case_id = rc.id
                           WHERE rc.status = 'PENDING'
                             AND (rc.post_id IS NOT NULL OR rc.comment_id IS NOT NULL)
                           GROUP BY rc.id
                        )
                        SELECT * FROM c"""
                                + where
                                + " ORDER BY cnt DESC, last_reported_at DESC, id DESC LIMIT :limit");
        if (after != null) {
            spec =
                    spec.param("cnt", after.count())
                            .param("last", Timestamp.from(after.lastReportedAt()))
                            .param("id", after.id());
        }
        return spec.param("limit", limit)
                .query(
                        (rs, n) ->
                                new PendingRow(
                                        rs.getLong("id"),
                                        rs.getString("target_type"),
                                        rs.getString("snapshot_title"),
                                        rs.getString("snapshot_content"),
                                        rs.getLong("target_author_id"),
                                        rs.getInt("cnt"),
                                        rs.getTimestamp("last_reported_at").toInstant()))
                .list();
    }

    /**
     * 처리된 사건 (자동 종료 포함).
     *
     * @param after 앞 페이지 마지막 항목의 {@code [handledAt, id]} (첫 페이지면 {@code null})
     */
    public List<CaseRow> handled(HandledKey after, int limit) {
        String where = after == null ? "" : " AND (handled_at, id) < (:at, :id)";
        JdbcClient.StatementSpec spec =
                jdbc.sql(
                        "SELECT "
                                + CaseRow.COLUMNS
                                + " FROM report_case WHERE status <> 'PENDING'"
                                + where
                                + " ORDER BY handled_at DESC, id DESC LIMIT :limit");
        if (after != null) {
            spec = spec.param("at", Timestamp.from(after.handledAt())).param("id", after.id());
        }
        return spec.param("limit", limit).query(CaseRow::map).list();
    }

    /** 사건마다 사유별 신고 수와 최근 신고 시각 (SQL 1번). 신고가 없는 사건은 결과에 없다. */
    public Map<Long, ReportStats> stats(Collection<Long> caseIds) {
        Map<Long, ReportStats> result = new HashMap<>();
        if (caseIds.isEmpty()) {
            return result;
        }
        jdbc.sql(
                        "SELECT case_id, reason, count(*) AS n, max(created_at) AS last FROM report"
                                + " WHERE case_id IN (:ids) GROUP BY case_id, reason")
                .param("ids", caseIds)
                .query(
                        (RowCallbackHandler)
                                rs -> {
                                    ReportStats stats =
                                            result.computeIfAbsent(
                                                    rs.getLong("case_id"), id -> new ReportStats());
                                    stats.add(
                                            ReportReason.valueOf(rs.getString("reason")),
                                            rs.getInt("n"),
                                            rs.getTimestamp("last").toInstant());
                                });
        return result;
    }

    /** 대기 사건 한 줄. */
    public record PendingRow(
            long id,
            String targetType,
            String snapshotTitle,
            String snapshotContent,
            long targetAuthorId,
            int count,
            Instant lastReportedAt) {}

    public record PendingKey(long count, Instant lastReportedAt, long id) {}

    public record HandledKey(Instant handledAt, long id) {}

    /** 한 사건의 사유별 수 (사유 순서대로). */
    public static final class ReportStats {
        private final Map<ReportReason, Integer> counts = new EnumMap<>(ReportReason.class);
        private int total;
        private Instant last;

        void add(ReportReason reason, int n, Instant at) {
            counts.merge(reason, n, Integer::sum);
            total += n;
            if (last == null || at.isAfter(last)) {
                last = at;
            }
        }

        public Map<ReportReason, Integer> counts() {
            return counts;
        }

        public int total() {
            return total;
        }

        public Instant last() {
            return last;
        }
    }
}
