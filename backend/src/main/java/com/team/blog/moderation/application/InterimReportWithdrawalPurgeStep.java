package com.team.blog.moderation.application;

import com.team.blog.shared.application.withdraw.WithdrawalPurgeStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * <b>임시 구현</b> — 탈퇴 정리 order 80: 내 콘텐츠의 대기 사건 종료와 내가 쓴 신고 설명 비우기 (015 contracts/purge-steps.md §2,
 * 13 §3-3 6-3). 사유는 남긴다.
 *
 * <p>이 단계의 주인은 <b>014-report-hide</b>(014 tasks T062 {@code ReportWithdrawalPurgeStep})다. 014가 머지되기
 * 전에도 015 정리 작업의 필수 단계(order 80)가 비지 않도록 015가 같은 SQL로 채워 둔다. 014가 진짜 클래스를 만들면 {@link
 * ConditionalOnMissingClass}로 이 Bean은 등록되지 않는다 — 그때 이 파일을 지운다.
 */
@Component
@ConditionalOnMissingClass("com.team.blog.moderation.application.ReportWithdrawalPurgeStep")
public class InterimReportWithdrawalPurgeStep implements WithdrawalPurgeStep {

    private static final Logger log =
            LoggerFactory.getLogger(InterimReportWithdrawalPurgeStep.class);

    private final JdbcClient jdbc;

    public InterimReportWithdrawalPurgeStep(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int order() {
        return 80;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void purge(long memberId) {
        int closed =
                jdbc.sql(
                                """
                                UPDATE report_case
                                   SET status = 'CLOSED_NO_TARGET', handled_at = now(), handled_by = NULL
                                 WHERE target_author_id = :m AND status = 'PENDING'
                                """)
                        .param("m", memberId)
                        .update();
        int cleared =
                jdbc.sql(
                                "UPDATE report SET detail = NULL WHERE reporter_id = :m AND detail IS"
                                        + " NOT NULL")
                        .param("m", memberId)
                        .update();
        log.info(
                "탈퇴 신고 정리(임시): memberId={} closedCases={} clearedDetails={}",
                memberId,
                closed,
                cleared);
    }
}
