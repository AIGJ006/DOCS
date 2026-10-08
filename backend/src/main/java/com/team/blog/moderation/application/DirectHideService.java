package com.team.blog.moderation.application;

import com.team.blog.interaction.application.CommentModerationService;
import com.team.blog.interaction.application.CommentModerationService.CommentSnapshot;
import com.team.blog.moderation.domain.CaseStatus;
import com.team.blog.moderation.domain.ReportReason;
import com.team.blog.moderation.domain.ReportTarget;
import com.team.blog.moderation.infra.ReportCaseRepository;
import com.team.blog.post.application.PostModerationService;
import com.team.blog.post.application.PostSnapshot;
import com.team.blog.shared.error.BusinessRuleException;
import com.team.blog.shared.error.NotFoundException;
import com.team.blog.shared.event.ContentUnhidden;
import com.team.blog.shared.event.ReportTargetType;
import com.team.blog.shared.security.Viewer;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 관리자 직접 숨김·해제 (014 T036·T044·T048, research R7, FR-020·FR-027·FR-030, Clarifications Q1).
 *
 * <p>숨김 {@code PUT …/hidden {reason}}: 관리자 확인 → 사유 형식 400 → 대상 없음 404 → 자기 콘텐츠 400 → 이미 숨김이면 200
 * 그대로(사건· 이벤트 없음) → 관리자가 볼 수 있는가(004 판정, 남의 비공개 글·휴지통 글·삭제된 자리·탈퇴 작성자 댓글은 404) → 한 트랜잭션: 대상 숨김 → 대기
 * 사건이 있으면({@code FOR UPDATE}) 그 사건을 {@code HIDDEN}으로 닫고 신고마다 {@code ReportResolved}, 없으면 신고 없는 사건을
 * 새로 만들어 바로 {@code HIDDEN}(스냅샷 포함) → {@code ContentHidden} 1번.
 *
 * <p>해제 {@code DELETE …/hidden}: 관리자 확인 → 대상 없음 404(휴지통 글은 있음) → 자기 콘텐츠 400 → 숨김이면 해제 + {@code
 * ContentUnhidden}, 아니면 200 그대로. 사건 상태는 {@code HIDDEN} 그대로 둔다(기록 유지). 알림 없음.
 */
@Service
public class DirectHideService {

    private static final Logger log = LoggerFactory.getLogger(DirectHideService.class);

    private final ModerationAccess access;
    private final ReportTargetResolver resolver;
    private final PostModerationService postModeration;
    private final CommentModerationService commentModeration;
    private final ReportCaseRepository cases;
    private final CaseCloser closer;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;
    private final ModerationProperties properties;
    private final Clock clock;

    public DirectHideService(
            ModerationAccess access,
            ReportTargetResolver resolver,
            PostModerationService postModeration,
            CommentModerationService commentModeration,
            ReportCaseRepository cases,
            CaseCloser closer,
            ApplicationEventPublisher events,
            TransactionTemplate tx,
            ModerationProperties properties,
            Clock clock) {
        this.access = access;
        this.resolver = resolver;
        this.postModeration = postModeration;
        this.commentModeration = commentModeration;
        this.cases = cases;
        this.closer = closer;
        this.events = events;
        this.tx = tx;
        this.properties = properties;
        this.clock = clock;
    }

    /** 대상 지금 상태 (판정용). */
    private record Current(long authorId, long postId, boolean hidden) {}

    public HiddenState hide(Viewer viewer, ReportTargetType type, long targetId, Object rawReason) {
        long admin = access.requireAdmin(viewer);
        ReportReason reason = CaseResolutionService.parseReason(rawReason);
        Current current = current(type, targetId, false);
        if (current.authorId() == admin) {
            throw new BusinessRuleException(ModerationReasonCode.CANNOT_MODERATE_OWN);
        }
        if (current.hidden()) {
            return new HiddenState(true, null);
        }
        ReportTarget target =
                resolver.resolve(type, targetId, viewer, properties.snapshotContentChars());
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Long caseId = tx.execute(status -> hideAndRecord(target, admin, reason, now));
        log.info("직접 숨김 type={} targetId={} caseId={}", type, targetId, caseId);
        return new HiddenState(true, caseId);
    }

    private Long hideAndRecord(ReportTarget target, long admin, ReportReason reason, Instant now) {
        CaseCloser.HideOutcome outcome =
                closer.hideTarget(target.type(), target.targetId(), admin, reason.name(), now);
        if (outcome == CaseCloser.HideOutcome.GONE) {
            throw new NotFoundException("직접 숨김: 처리 중 대상이 사라짐");
        }
        if (outcome == CaseCloser.HideOutcome.ALREADY_HIDDEN) {
            return null;
        }
        Optional<Long> pending = cases.lockPending(target.type(), target.targetId());
        if (pending.isPresent()) {
            closer.close(
                    pending.get(), CaseStatus.HIDDEN, admin, target.type(), target.targetId(), now);
            return pending.get();
        }
        return cases.insertHidden(target, admin, now);
    }

    public HiddenState unhide(Viewer viewer, ReportTargetType type, long targetId) {
        long admin = access.requireAdmin(viewer);
        Current current = current(type, targetId, true);
        if (current.authorId() == admin) {
            throw new BusinessRuleException(ModerationReasonCode.CANNOT_MODERATE_OWN);
        }
        if (!current.hidden()) {
            return new HiddenState(false, null);
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        tx.executeWithoutResult(
                status -> {
                    boolean changed =
                            type == ReportTargetType.POST
                                    ? postModeration.unhide(targetId)
                                    : commentModeration.unhide(targetId);
                    if (changed) {
                        events.publishEvent(
                                new ContentUnhidden(
                                        type, targetId, current.authorId(), current.postId(), now));
                    }
                });
        log.info("숨김 해제 type={} targetId={}", type, targetId);
        return new HiddenState(false, null);
    }

    /** 대상 지금 상태. 없으면 404. 해제(forUnhide)는 삭제된 자리 댓글을 없음으로 본다(007이 해제할 수 없음). */
    private Current current(ReportTargetType type, long targetId, boolean forUnhide) {
        if (type == ReportTargetType.POST) {
            PostSnapshot post =
                    postModeration
                            .snapshot(targetId, 1)
                            .orElseThrow(() -> new NotFoundException("숨김: 글 없음"));
            return new Current(post.authorId(), targetId, post.hidden());
        }
        CommentSnapshot comment =
                commentModeration
                        .snapshot(targetId)
                        .orElseThrow(() -> new NotFoundException("숨김: 댓글 없음"));
        if (comment.deleted() && forUnhide) {
            throw new NotFoundException("숨김 해제: 삭제된 자리");
        }
        return new Current(comment.authorId(), comment.postId(), comment.hidden());
    }
}
