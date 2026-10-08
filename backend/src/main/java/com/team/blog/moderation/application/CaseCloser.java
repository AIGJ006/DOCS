package com.team.blog.moderation.application;

import com.team.blog.interaction.application.CommentModerationService;
import com.team.blog.interaction.application.CommentModerationService.CommentSnapshot;
import com.team.blog.moderation.domain.CaseStatus;
import com.team.blog.moderation.infra.ReportCaseRepository;
import com.team.blog.moderation.infra.ReportRepository;
import com.team.blog.moderation.infra.ReportRow;
import com.team.blog.post.application.PostModerationService;
import com.team.blog.post.application.PostSnapshot;
import com.team.blog.shared.event.ContentHidden;
import com.team.blog.shared.event.ReportResolved;
import com.team.blog.shared.event.ReportResult;
import com.team.blog.shared.event.ReportTargetType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 처리(R6 7~9)와 직접 숨김(R7)이 함께 쓰는 단계: 대상 숨기기, 사건 닫기와 신고마다 {@link ReportResolved}. 호출한 쪽 트랜잭션 안에서 돈다 —
 * 이벤트는 그 트랜잭션 안에서 발행하고 구독(011)은 커밋 뒤다.
 */
@Component
class CaseCloser {

    /** 대상 숨기기 결과. */
    enum HideOutcome {
        /** 이번에 숨겼다 ({@code ContentHidden} 발행함). */
        HIDDEN_NOW,
        /** 이미 숨김이었다. */
        ALREADY_HIDDEN,
        /** 대상이 없다 (행 삭제·삭제된 자리 댓글). */
        GONE
    }

    private final ReportCaseRepository cases;
    private final ReportRepository reports;
    private final PostModerationService postModeration;
    private final CommentModerationService commentModeration;
    private final ApplicationEventPublisher events;

    CaseCloser(
            ReportCaseRepository cases,
            ReportRepository reports,
            PostModerationService postModeration,
            CommentModerationService commentModeration,
            ApplicationEventPublisher events) {
        this.cases = cases;
        this.reports = reports;
        this.postModeration = postModeration;
        this.commentModeration = commentModeration;
        this.events = events;
    }

    /**
     * 대상을 숨긴다. 대상이 없으면 아무것도 바꾸지 않고 {@link HideOutcome#GONE} — 다른 모듈 Service가 예외를 던지면 트랜잭션이 롤백 전용이
     * 되므로 먼저 상태를 읽어 거른다.
     */
    HideOutcome hideTarget(
            ReportTargetType type, long targetId, long admin, String reason, Instant now) {
        if (type == ReportTargetType.POST) {
            Optional<PostSnapshot> post = postModeration.snapshot(targetId, 1);
            if (post.isEmpty()) {
                return HideOutcome.GONE;
            }
            if (!postModeration.hide(targetId, admin, reason, now)) {
                return HideOutcome.ALREADY_HIDDEN;
            }
            events.publishEvent(
                    new ContentHidden(
                            ReportTargetType.POST, targetId, post.get().authorId(), targetId, now));
            return HideOutcome.HIDDEN_NOW;
        }
        Optional<CommentSnapshot> comment = commentModeration.snapshot(targetId);
        if (comment.isEmpty() || comment.get().deleted()) {
            return HideOutcome.GONE;
        }
        if (!commentModeration.hide(targetId, admin, reason, now)) {
            return HideOutcome.ALREADY_HIDDEN;
        }
        events.publishEvent(
                new ContentHidden(
                        ReportTargetType.COMMENT,
                        targetId,
                        comment.get().authorId(),
                        comment.get().postId(),
                        now));
        return HideOutcome.HIDDEN_NOW;
    }

    /** 사건을 숨김·반려로 닫고 신고마다 {@link ReportResolved}를 낸다(FR-028). */
    void close(
            long caseId,
            CaseStatus status,
            long admin,
            ReportTargetType type,
            long targetId,
            Instant now) {
        cases.close(caseId, status, admin, now);
        ReportResult result =
                status == CaseStatus.HIDDEN ? ReportResult.ACTION_TAKEN : ReportResult.NO_VIOLATION;
        for (ReportRow report : reports.findByCase(caseId)) {
            events.publishEvent(
                    new ReportResolved(
                            report.id(), report.reporterId(), type, targetId, result, now));
        }
    }

    /** 대상 없음으로 닫는다 (이벤트 없음). */
    void closeNoTarget(long caseId, Instant now) {
        cases.close(caseId, CaseStatus.CLOSED_NO_TARGET, null, now);
    }
}
