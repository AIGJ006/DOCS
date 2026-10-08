package com.team.blog.moderation.application;

import com.team.blog.moderation.infra.ReportCaseRepository;
import com.team.blog.moderation.infra.ReportRepository;
import com.team.blog.shared.application.withdraw.WithdrawalPurgeStep;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴 30일 정리 order 80 (014 T062, 015 contracts/purge-steps.md §2, contracts/moderation-sql.md §5 셋째
 * 줄, 13 §3-3 6-3): 그 회원 콘텐츠의 대기 사건을 "대상 없음"으로 닫고, 그 회원이 쓴 신고의 설명을 비운다. 사유·사건·신고 행은 남는다. 이벤트 없음, 로그는
 * 회원 번호와 건수만. 멱등이다(두 번 불러도 같은 결과).
 */
@Component
public class ReportWithdrawalPurgeStep implements WithdrawalPurgeStep {

    static final int ORDER = 80;

    private static final Logger log = LoggerFactory.getLogger(ReportWithdrawalPurgeStep.class);

    private final ReportCaseRepository cases;
    private final ReportRepository reports;
    private final Clock clock;

    public ReportWithdrawalPurgeStep(
            ReportCaseRepository cases, ReportRepository reports, Clock clock) {
        this.cases = cases;
        this.reports = reports;
        this.clock = clock;
    }

    @Override
    public int order() {
        return ORDER;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void purge(long memberId) {
        int closed = cases.closeNoTargetForAuthor(memberId, clock.instant());
        int cleared = reports.clearDetailsOfReporter(memberId);
        log.info(
                "탈퇴 신고 정리 memberId={} closedCases={} clearedDetails={}", memberId, closed, cleared);
    }
}
