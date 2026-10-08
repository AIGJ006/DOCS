package com.team.blog.moderation.application;

import com.team.blog.moderation.domain.ReportReason;
import com.team.blog.moderation.domain.ReportTarget;
import com.team.blog.moderation.infra.ReportCaseRepository;
import com.team.blog.moderation.infra.ReportRepository;
import com.team.blog.post.domain.PostNotFoundException;
import com.team.blog.shared.error.ApiException;
import com.team.blog.shared.error.BusinessRuleException;
import com.team.blog.shared.error.CommonReasonCode;
import com.team.blog.shared.error.FieldError;
import com.team.blog.shared.error.TemporarilyUnavailableException;
import com.team.blog.shared.error.ValidationException;
import com.team.blog.shared.event.ReportTargetType;
import com.team.blog.shared.infra.ratelimit.RateLimiter;
import com.team.blog.shared.security.AccountStatusGuard;
import com.team.blog.shared.security.ActionKind;
import com.team.blog.shared.security.Viewer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 신고 접수 (014 T021, {@code POST /api/reports}, research R3·R4, FR-001~FR-011).
 *
 * <p>판정 순서(처음 걸린 단계의 응답):
 *
 * <ol>
 *   <li>401 {@code LOGIN_REQUIRED}
 *   <li>403 — 001 {@link AccountStatusGuard}({@link ActionKind#CONTENT_WRITE})
 *   <li>400 {@code VALIDATION_FAILED} — 대상 종류·번호·사유 형식, 설명 200자 초과
 *   <li>404 — 볼 수 없는 대상 ({@link ReportTargetResolver})
 *   <li>400 {@code CANNOT_REPORT_OWN}
 *   <li>400 {@code VALIDATION_FAILED} + {@code errors[{field: detail, code:
 *       REPORT_DETAIL_REQUIRED}]}
 *   <li>429 {@code TOO_MANY_REQUESTS} — {@code ratelimit:report:{memberId}:1m} 5번·{@code …:1d}
 *       50번(Redis 장애면 통과). 여기까지 온 요청만 센다
 * </ol>
 *
 * 그다음 한 트랜잭션: 대기 사건 {@code INSERT … ON CONFLICT DO NOTHING}(만들 때만 스냅샷) → 없으면 기존 대기 사건 {@code FOR
 * UPDATE} → 신고 {@code INSERT … ON CONFLICT (case_id, reporter_id) DO NOTHING}. 1과 2 사이에 사건이 처리됐으면 한
 * 번 더 시도한다. 새 신고든 중복이든 200이다. 접수는 이벤트·알림을 만들지 않는다(FR-010). 신고 수와 관계없이 자동으로 숨기지 않는다(FR-017).
 */
@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    /** 요청 제한 키 접두어 (data-model §2). */
    public static final String RATE_LIMIT_PREFIX = "ratelimit:report:";

    private static final int MAX_ATTEMPTS = 2;

    private final AccountStatusGuard accountStatusGuard;
    private final ReportTargetResolver resolver;
    private final RateLimiter rateLimiter;
    private final ReportCaseRepository cases;
    private final ReportRepository reports;
    private final TransactionTemplate tx;
    private final ModerationProperties properties;
    private final Clock clock;

    public ReportService(
            AccountStatusGuard accountStatusGuard,
            ReportTargetResolver resolver,
            RateLimiter rateLimiter,
            ReportCaseRepository cases,
            ReportRepository reports,
            TransactionTemplate tx,
            ModerationProperties properties,
            Clock clock) {
        this.accountStatusGuard = accountStatusGuard;
        this.resolver = resolver;
        this.rateLimiter = rateLimiter;
        this.cases = cases;
        this.reports = reports;
        this.tx = tx;
        this.properties = properties;
        this.clock = clock;
    }

    /** 신고를 접수한다. 같은 대기 사건에 같은 회원이 다시 신고해도 성공(아무것도 바뀌지 않음). */
    public void report(
            Viewer viewer, Object targetType, Object targetId, Object reason, Object detail) {
        if (viewer == null || !viewer.isAuthenticated()) {
            throw new ApiException(CommonReasonCode.LOGIN_REQUIRED);
        }
        long me = viewer.id();
        accountStatusGuard.requireActive(me, ActionKind.CONTENT_WRITE);
        ReportCommand command = parse(targetType, targetId, reason, detail);
        ReportTarget target =
                resolver.resolve(
                        command.targetType(),
                        command.targetId(),
                        viewer,
                        properties.snapshotContentChars());
        if (target.authorId() == me) {
            throw new BusinessRuleException(ModerationReasonCode.CANNOT_REPORT_OWN);
        }
        String savedDetail = command.reason() == ReportReason.OTHER ? command.detail() : null;
        if (command.reason() == ReportReason.OTHER && savedDetail == null) {
            throw ValidationException.of(
                    "detail",
                    ModerationReasonCode.REPORT_DETAIL_REQUIRED.code(),
                    ModerationReasonCode.REPORT_DETAIL_REQUIRED.defaultMessage());
        }
        ModerationProperties.RateLimit limit = properties.rateLimit();
        rateLimiter.acquireOrThrow(
                RATE_LIMIT_PREFIX + me + ":1m", limit.perMinute(), Duration.ofMinutes(1));
        rateLimiter.acquireOrThrow(
                RATE_LIMIT_PREFIX + me + ":1d", limit.perDay(), Duration.ofDays(1));

        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        try {
            Boolean created =
                    tx.execute(status -> save(target, me, command.reason(), savedDetail, now));
            log.debug(
                    "신고 접수 type={} targetId={} new={}", target.type(), target.targetId(), created);
        } catch (DataIntegrityViolationException e) {
            // 판정 뒤 대상이 완전히 지워져 FK가 막은 경우 — 없는 대상과 같은 404
            throw new PostNotFoundException("신고: 처리 중 대상이 사라짐");
        }
    }

    private boolean save(
            ReportTarget target, long me, ReportReason reason, String detail, Instant now) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            Optional<Long> created = cases.insertPending(target, now);
            Optional<Long> caseId =
                    created.isPresent()
                            ? created
                            : cases.lockPending(target.type(), target.targetId());
            if (caseId.isPresent()) {
                return reports.insertIfAbsent(caseId.get(), me, reason, detail, now);
            }
            // 만들기 시도와 잠금 사이에 대기 사건이 처리됐다 — 다음 시도는 새 사건이 된다
        }
        throw new TemporarilyUnavailableException(new IllegalStateException("신고 사건을 만들지 못함"));
    }

    /** 형식 검사 (research R3 ③). 칸 오류는 모아서 한 번에. */
    ReportCommand parse(Object rawType, Object rawId, Object rawReason, Object rawDetail) {
        List<FieldError> errors = new ArrayList<>();
        ReportTargetType type = enumOf(ReportTargetType.class, rawType, "targetType", errors);
        long id = 0;
        if (rawId == null) {
            errors.add(new FieldError("targetId", "REQUIRED", "필수 값이에요"));
        } else {
            id = positiveLong(rawId);
            if (id < 1) {
                errors.add(new FieldError("targetId", "INVALID_VALUE", "입력한 내용을 확인해 주세요"));
            }
        }
        ReportReason reason = enumOf(ReportReason.class, rawReason, "reason", errors);
        String detail = null;
        if (rawDetail != null) {
            if (!(rawDetail instanceof String text)) {
                errors.add(new FieldError("detail", "INVALID_VALUE", "입력한 내용을 확인해 주세요"));
            } else {
                String trimmed = text.strip();
                if (trimmed.codePointCount(0, trimmed.length()) > properties.detailMaxChars()) {
                    errors.add(
                            new FieldError(
                                    "detail",
                                    "TOO_LONG",
                                    "설명은 " + properties.detailMaxChars() + "자까지 쓸 수 있어요"));
                }
                detail = trimmed.isEmpty() ? null : trimmed;
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        return new ReportCommand(type, id, reason, detail);
    }

    private static <E extends Enum<E>> E enumOf(
            Class<E> type, Object raw, String field, List<FieldError> errors) {
        if (raw == null) {
            errors.add(new FieldError(field, "REQUIRED", "필수 값이에요"));
            return null;
        }
        if (raw instanceof String text) {
            try {
                return Enum.valueOf(type, text);
            } catch (IllegalArgumentException ignored) {
                // 아래 칸 오류
            }
        }
        errors.add(new FieldError(field, "INVALID_VALUE", "입력한 내용을 확인해 주세요"));
        return null;
    }

    /** 정수(JSON 숫자 또는 숫자 문자열)면 그 값, 아니면 0. */
    private static long positiveLong(Object raw) {
        if (raw instanceof Integer || raw instanceof Long || raw instanceof Short) {
            return ((Number) raw).longValue();
        }
        if (raw instanceof java.math.BigInteger big) {
            return big.bitLength() < 63 ? big.longValue() : 0;
        }
        if (raw instanceof String text && text.matches("[0-9]{1,18}")) {
            return Long.parseLong(text);
        }
        return 0;
    }
}
