package com.team.blog.moderation.application;

import com.team.blog.moderation.infra.ReportCaseRepository;
import com.team.blog.shared.event.CommentDeleted;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 댓글이 지워지면(자리로 남김 포함) 그 댓글의 대기 사건을 "대상 없음"으로 닫는다 (014 FR-031, contracts/moderation-sql.md §5 둘째 줄).
 * 007 {@code CommentDeleted}를 커밋 뒤 비동기로 받는다. 실패는 WARN만 남기고, 남은 고아 사건은 매일 정리 작업({@link
 * ReportSnapshotCleanupJob})이 닫는다.
 */
@Component
public class OrphanCaseCloser {

    private static final Logger log = LoggerFactory.getLogger(OrphanCaseCloser.class);

    private final ReportCaseRepository cases;
    private final Clock clock;

    public OrphanCaseCloser(ReportCaseRepository cases, Clock clock) {
        this.cases = cases;
        this.clock = clock;
    }

    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(CommentDeleted e) {
        try {
            int closed = cases.closeNoTargetForDeletedComment(e.commentId(), clock.instant());
            if (closed > 0) {
                log.info("댓글 삭제로 신고 사건 닫음: commentId={} closed={}", e.commentId(), closed);
            }
        } catch (RuntimeException ex) {
            log.warn("댓글 삭제 신고 사건 닫기 실패: commentId={}", e.commentId(), ex);
        }
    }
}
