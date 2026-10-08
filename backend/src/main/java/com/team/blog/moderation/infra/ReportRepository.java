package com.team.blog.moderation.infra;

import com.team.blog.moderation.domain.ReportReason;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** {@code report} 저장소 (014 T013, contracts/moderation-sql.md §1-3·§3·§5·§8). */
@Repository
public class ReportRepository {

    private final JdbcClient jdbc;

    public ReportRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 신고 한 건 (§1-3). 같은 사건에 같은 회원이 이미 신고했으면 아무것도 바꾸지 않는다({@code uq_report_case_reporter}).
     *
     * @return 새로 넣었으면 true
     */
    public boolean insertIfAbsent(
            long caseId, long reporterId, ReportReason reason, String detail, Instant now) {
        return jdbc.sql(
                                "INSERT INTO report (case_id, reporter_id, reason, detail, created_at)"
                                        + " VALUES (:c, :r, :reason, :detail, :now)"
                                        + " ON CONFLICT (case_id, reporter_id) DO NOTHING")
                        .param("c", caseId)
                        .param("r", reporterId)
                        .param("reason", reason.name())
                        .param("detail", detail)
                        .param("now", Timestamp.from(now))
                        .update()
                == 1;
    }

    /** 그 사건의 신고들 (오래된 순). */
    public List<ReportRow> findByCase(long caseId) {
        return jdbc.sql(
                        "SELECT id, case_id, reporter_id, reason, detail, created_at FROM report"
                                + " WHERE case_id = :c ORDER BY created_at, id")
                .param("c", caseId)
                .query(
                        (rs, n) ->
                                new ReportRow(
                                        rs.getLong("id"),
                                        rs.getLong("case_id"),
                                        rs.getLong("reporter_id"),
                                        ReportReason.valueOf(rs.getString("reason")),
                                        rs.getString("detail"),
                                        rs.getTimestamp("created_at").toInstant()))
                .list();
    }

    /** 탈퇴 정리 — 그 회원이 쓴 신고 설명 비우기 (§5 셋째 줄). 사유는 남긴다. */
    public int clearDetailsOfReporter(long memberId) {
        return jdbc.sql(
                        "UPDATE report SET detail = NULL WHERE reporter_id = :m AND detail IS NOT"
                                + " NULL")
                .param("m", memberId)
                .update();
    }

    /** 보관 기간이 지난 처리된 사건의 신고 설명 비우기 (§8-2, 한 번에 {@code batch}개). */
    public int clearDetails(Instant cutoff, int batch) {
        return jdbc.sql(
                        """
                        UPDATE report SET detail = NULL
                         WHERE id IN (SELECT r.id FROM report r JOIN report_case rc ON rc.id = r.case_id
                                       WHERE r.detail IS NOT NULL AND rc.status <> 'PENDING'
                                         AND rc.handled_at < :cutoff
                                       LIMIT :batch)
                        """)
                .param("cutoff", Timestamp.from(cutoff))
                .param("batch", batch)
                .update();
    }
}
