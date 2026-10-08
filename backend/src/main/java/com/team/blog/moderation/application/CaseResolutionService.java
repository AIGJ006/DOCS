package com.team.blog.moderation.application;

import com.team.blog.moderation.domain.CaseStatus;
import com.team.blog.moderation.domain.ReportReason;
import com.team.blog.moderation.domain.ResolutionAction;
import com.team.blog.moderation.infra.CaseRow;
import com.team.blog.moderation.infra.ReportCaseRepository;
import com.team.blog.moderation.infra.ReportRepository;
import com.team.blog.moderation.infra.ReportRow;
import com.team.blog.shared.error.ApiException;
import com.team.blog.shared.error.BusinessRuleException;
import com.team.blog.shared.error.FieldError;
import com.team.blog.shared.error.NotFoundException;
import com.team.blog.shared.error.ValidationException;
import com.team.blog.shared.security.Viewer;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 관리자 처리 — 숨기기·문제없음 (014 T035, research R6, FR-016·FR-019·FR-028·FR-029, SC-003).
 *
 * <ol>
 *   <li>관리자 확인({@link ModerationAccess}), 형식(HIDE면 사유 필수) — 400
 *   <li>사건 {@code FOR UPDATE} — 없음 404
 *   <li>{@code PENDING}이 아님 → 409 {@code REPORT_ALREADY_HANDLED} {@code details {status}}. 늦은 요청은
 *       아무것도 바꾸지 않는다
 *   <li>대상이 사라짐 → {@code CLOSED_NO_TARGET}으로 닫고(커밋) 409 {@code details {status: CLOSED_NO_TARGET}}
 *   <li>대상 작성자 = 관리자 → 400 {@code CANNOT_MODERATE_OWN}
 *   <li>신고자가 관리자 자신뿐 → 400 {@code CANNOT_HANDLE_OWN_REPORT}
 *   <li>HIDE: 대상 숨김(삭제된 자리 댓글이면 4와 같음) → 사건 {@code HIDDEN} + 신고마다 {@code
 *       ReportResolved(ACTION_TAKEN)} + 새로 숨겼으면 {@code ContentHidden} 1번. REJECT: 대상 그대로, {@code
 *       REJECTED} + {@code NO_VIOLATION}
 * </ol>
 */
@Service
public class CaseResolutionService {

    private static final Logger log = LoggerFactory.getLogger(CaseResolutionService.class);

    private final ModerationAccess access;
    private final ReportCaseRepository cases;
    private final ReportRepository reports;
    private final CaseCloser closer;
    private final CaseQueryService queries;
    private final TransactionTemplate tx;
    private final Clock clock;

    public CaseResolutionService(
            ModerationAccess access,
            ReportCaseRepository cases,
            ReportRepository reports,
            CaseCloser closer,
            CaseQueryService queries,
            TransactionTemplate tx,
            Clock clock) {
        this.access = access;
        this.cases = cases;
        this.reports = reports;
        this.closer = closer;
        this.queries = queries;
        this.tx = tx;
        this.clock = clock;
    }

    /** 트랜잭션 결과 — 409는 대상 없음 종료를 커밋한 뒤에 던진다. */
    private enum Result {
        DONE,
        CLOSED_NO_TARGET
    }

    /**
     * @return 처리 뒤 상세
     */
    public CaseDetail resolve(Viewer viewer, long caseId, Object rawAction, Object rawReason) {
        long admin = access.requireAdmin(viewer);
        ResolutionAction action = parseAction(rawAction, rawReason);
        ReportReason reason = action == ResolutionAction.HIDE ? parseReason(rawReason) : null;
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);

        Result result = tx.execute(status -> apply(caseId, admin, action, reason, now));
        if (result == Result.CLOSED_NO_TARGET) {
            throw alreadyHandled(CaseStatus.CLOSED_NO_TARGET);
        }
        log.info("신고 사건 처리 caseId={} action={}", caseId, action);
        return queries.detailAsAdmin(admin, caseId);
    }

    private Result apply(
            long caseId, long admin, ResolutionAction action, ReportReason reason, Instant now) {
        CaseRow row = cases.lock(caseId).orElseThrow(() -> new NotFoundException("처리: 사건 없음"));
        if (row.status() != CaseStatus.PENDING) {
            throw alreadyHandled(row.status());
        }
        if (row.isOrphan()) {
            closer.closeNoTarget(caseId, now);
            return Result.CLOSED_NO_TARGET;
        }
        if (row.targetAuthorId() == admin) {
            throw new BusinessRuleException(ModerationReasonCode.CANNOT_MODERATE_OWN);
        }
        List<ReportRow> rows = reports.findByCase(caseId);
        if (!rows.isEmpty() && rows.stream().allMatch(r -> r.reporterId() == admin)) {
            throw new BusinessRuleException(ModerationReasonCode.CANNOT_HANDLE_OWN_REPORT);
        }
        long targetId = row.targetId();
        if (action == ResolutionAction.HIDE) {
            CaseCloser.HideOutcome outcome =
                    closer.hideTarget(row.targetType(), targetId, admin, reason.name(), now);
            if (outcome == CaseCloser.HideOutcome.GONE) {
                closer.closeNoTarget(caseId, now);
                return Result.CLOSED_NO_TARGET;
            }
            closer.close(caseId, CaseStatus.HIDDEN, admin, row.targetType(), targetId, now);
        } else {
            closer.close(caseId, CaseStatus.REJECTED, admin, row.targetType(), targetId, now);
        }
        return Result.DONE;
    }

    static ApiException alreadyHandled(CaseStatus status) {
        return new BusinessRuleException(
                ModerationReasonCode.REPORT_ALREADY_HANDLED, Map.of("status", status.name()));
    }

    private static ResolutionAction parseAction(Object rawAction, Object rawReason) {
        List<FieldError> errors = new ArrayList<>();
        ResolutionAction action = null;
        if (rawAction == null) {
            errors.add(new FieldError("action", "REQUIRED", "필수 값이에요"));
        } else {
            action = enumOrNull(ResolutionAction.class, rawAction);
            if (action == null) {
                errors.add(new FieldError("action", "INVALID_VALUE", "입력한 내용을 확인해 주세요"));
            }
        }
        if (action == ResolutionAction.HIDE) {
            if (rawReason == null) {
                errors.add(new FieldError("reason", "REQUIRED", "숨김 사유를 골라 주세요"));
            } else if (enumOrNull(ReportReason.class, rawReason) == null) {
                errors.add(new FieldError("reason", "INVALID_VALUE", "입력한 내용을 확인해 주세요"));
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        return action;
    }

    /** 숨김 사유 형식 (직접 숨김도 쓴다). */
    static ReportReason parseReason(Object rawReason) {
        if (rawReason == null) {
            throw ValidationException.of("reason", "REQUIRED", "숨김 사유를 골라 주세요");
        }
        ReportReason reason = enumOrNull(ReportReason.class, rawReason);
        if (reason == null) {
            throw ValidationException.of("reason", "INVALID_VALUE", "입력한 내용을 확인해 주세요");
        }
        return reason;
    }

    static <E extends Enum<E>> E enumOrNull(Class<E> type, Object raw) {
        if (!(raw instanceof String text)) {
            return null;
        }
        try {
            return Enum.valueOf(type, text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
