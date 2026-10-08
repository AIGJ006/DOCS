package com.team.blog.moderation.infra;

import com.team.blog.moderation.domain.CaseStatus;
import com.team.blog.moderation.domain.ReportTarget;
import com.team.blog.shared.event.ReportTargetType;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@code report_case} 저장소 (014 T013, contracts/moderation-sql.md §1·§3·§4·§5·§8). 대상마다 대기({@code
 * PENDING}) 사건은 하나다 — V1 부분 UNIQUE {@code uq_report_case_open_post}·{@code
 * uq_report_case_open_comment}가 지킨다.
 */
@Repository
public class ReportCaseRepository {

    /** 대상 없음 자동 종료 (§5). 이벤트·알림 없음, 처리 관리자 NULL. */
    private static final String CLOSE_NO_TARGET =
            "UPDATE report_case SET status = 'CLOSED_NO_TARGET', handled_at = :now,"
                    + " handled_by = NULL WHERE status = 'PENDING' AND ";

    private final JdbcClient jdbc;

    public ReportCaseRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 대기 사건 만들기 시도 (§1-1). 그 대상에 대기 사건이 이미 있으면 빈 값({@code ON CONFLICT … DO NOTHING}). 스냅샷은 만들 때만
     * 들어간다.
     */
    public Optional<Long> insertPending(ReportTarget target, Instant now) {
        String column = column(target.type());
        return jdbc.sql(
                        "INSERT INTO report_case (target_type, "
                                + column
                                + ", target_author_id, snapshot_title, snapshot_content,"
                                + " created_at) VALUES (:type, :target, :author, :title, :content,"
                                + " :now) ON CONFLICT ("
                                + column
                                + ") WHERE status = 'PENDING' AND "
                                + column
                                + " IS NOT NULL DO NOTHING RETURNING id")
                .param("type", target.type().name())
                .param("target", target.targetId())
                .param("author", target.authorId())
                .param("title", target.snapshotTitle())
                .param("content", target.snapshotContent())
                .param("now", Timestamp.from(now))
                .query(Long.class)
                .optional();
    }

    /** 그 대상의 대기 사건을 잠근다 (§1-2·§4). 없으면 빈 값. */
    public Optional<Long> lockPending(ReportTargetType type, long targetId) {
        return jdbc.sql(
                        "SELECT id FROM report_case WHERE "
                                + column(type)
                                + " = :target AND status = 'PENDING' FOR UPDATE")
                .param("target", targetId)
                .query(Long.class)
                .optional();
    }

    /** 신고 없이 바로 숨김으로 닫힌 사건 (§4, 직접 숨김). */
    public long insertHidden(ReportTarget target, long adminId, Instant now) {
        String column = column(target.type());
        return jdbc.sql(
                        "INSERT INTO report_case (target_type, "
                                + column
                                + ", target_author_id, snapshot_title, snapshot_content, status,"
                                + " handled_by, handled_at, created_at) VALUES (:type, :target,"
                                + " :author, :title, :content, 'HIDDEN', :admin, :now, :now)"
                                + " RETURNING id")
                .param("type", target.type().name())
                .param("target", target.targetId())
                .param("author", target.authorId())
                .param("title", target.snapshotTitle())
                .param("content", target.snapshotContent())
                .param("admin", adminId)
                .param("now", Timestamp.from(now))
                .query(Long.class)
                .single();
    }

    public Optional<CaseRow> find(long caseId) {
        return jdbc.sql("SELECT " + CaseRow.COLUMNS + " FROM report_case WHERE id = :id")
                .param("id", caseId)
                .query(CaseRow::map)
                .optional();
    }

    /** 처리용 잠금 (§3). */
    public Optional<CaseRow> lock(long caseId) {
        return jdbc.sql("SELECT " + CaseRow.COLUMNS + " FROM report_case WHERE id = :id FOR UPDATE")
                .param("id", caseId)
                .query(CaseRow::map)
                .optional();
    }

    /** 처리 결과 기록 (§3). */
    public void close(long caseId, CaseStatus status, Long adminId, Instant now) {
        jdbc.sql(
                        "UPDATE report_case SET status = :status, handled_by = :admin, handled_at ="
                                + " :now WHERE id = :id")
                .param("status", status.name())
                .param("admin", adminId)
                .param("now", Timestamp.from(now))
                .param("id", caseId)
                .update();
    }

    /** 글 완전 삭제 전 — 그 글 대상 대기 사건 (§5 첫 줄, 006 order 10). */
    public int closeNoTargetForPost(long postId, Instant now) {
        return jdbc.sql(CLOSE_NO_TARGET + "post_id = :postId")
                .param("now", Timestamp.from(now))
                .param("postId", postId)
                .update();
    }

    /** 그 댓글들 대상 대기 사건 (호출한 쪽이 1,000개 이하로 나눠 보낸다 — 바인드 인자 한계). */
    public int closeNoTargetForComments(Collection<Long> commentIds, Instant now) {
        if (commentIds.isEmpty()) {
            return 0;
        }
        return jdbc.sql(CLOSE_NO_TARGET + "comment_id IN (:ids)")
                .param("now", Timestamp.from(now))
                .param("ids", commentIds)
                .update();
    }

    /** 댓글 본인 삭제 뒤 (§5 둘째 줄). 행이 지워졌으면 FK가 이미 NULL이라 고아 조건이 잡는다. */
    public int closeNoTargetForDeletedComment(long commentId, Instant now) {
        return jdbc.sql(
                        CLOSE_NO_TARGET
                                + "(comment_id = :id OR (post_id IS NULL AND comment_id IS NULL))")
                .param("now", Timestamp.from(now))
                .param("id", commentId)
                .update();
    }

    /** 탈퇴 정리 — 그 회원 콘텐츠의 대기 사건 (§5 셋째 줄, 015 order 80). */
    public int closeNoTargetForAuthor(long memberId, Instant now) {
        return jdbc.sql(CLOSE_NO_TARGET + "target_author_id = :m")
                .param("now", Timestamp.from(now))
                .param("m", memberId)
                .update();
    }

    /** 고아 사건 (§5 넷째 줄, 매일 배치 1단계). */
    public int closeOrphans(Instant now) {
        return jdbc.sql(CLOSE_NO_TARGET + "post_id IS NULL AND comment_id IS NULL")
                .param("now", Timestamp.from(now))
                .update();
    }

    /** 대상 작성자의 숨김으로 닫힌 사건 수 ("이전에 숨겨진 콘텐츠 수"). */
    public int hiddenCountOfAuthor(long authorId) {
        return jdbc.sql(
                        "SELECT count(*) FROM report_case WHERE target_author_id = :a AND status ="
                                + " 'HIDDEN'")
                .param("a", authorId)
                .query(Integer.class)
                .single();
    }

    /** 보관 기간이 지난 처리된 사건의 스냅샷 비우기 (§8-3, 한 번에 {@code batch}개). */
    public int clearSnapshots(Instant cutoff, int batch) {
        return jdbc.sql(
                        """
                        UPDATE report_case SET snapshot_title = NULL, snapshot_content = NULL
                         WHERE id IN (SELECT id FROM report_case
                                       WHERE status <> 'PENDING' AND handled_at < :cutoff
                                         AND (snapshot_title IS NOT NULL OR snapshot_content IS NOT NULL)
                                       LIMIT :batch)
                        """)
                .param("cutoff", Timestamp.from(cutoff))
                .param("batch", batch)
                .update();
    }

    /** 대상 종류의 FK 칼럼 이름. */
    static String column(ReportTargetType type) {
        return type == ReportTargetType.POST ? "post_id" : "comment_id";
    }

    /** 테스트·조회용: 대상의 사건 상태들. */
    public List<CaseStatus> statusesOf(ReportTargetType type, long targetId) {
        return jdbc.sql(
                        "SELECT status FROM report_case WHERE "
                                + column(type)
                                + " = :t ORDER BY id")
                .param("t", targetId)
                .query((rs, n) -> CaseStatus.valueOf(rs.getString(1)))
                .list();
    }
}
