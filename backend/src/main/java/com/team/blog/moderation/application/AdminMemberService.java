package com.team.blog.moderation.application;

import com.team.blog.account.application.AdminMemberInfo;
import com.team.blog.account.application.MemberQueryService;
import com.team.blog.account.application.SuspensionDuration;
import com.team.blog.account.application.SuspensionRecord;
import com.team.blog.account.application.SuspensionService;
import com.team.blog.moderation.infra.ReportCaseRepository;
import com.team.blog.shared.error.FieldError;
import com.team.blog.shared.error.NotFoundException;
import com.team.blog.shared.error.ValidationException;
import com.team.blog.shared.security.Viewer;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 관리자 회원 화면 (014 T053, research R10, FR-035~FR-041): 회원 정보·숨겨진 수·정지 이력 조회, 정지·해제. 정지 자체(세션 삭제 포함)는
 * account {@link SuspensionService}가 한다.
 *
 * <p>판정 순서: 관리자 확인 → 회원(없음·익명 처리 404) → 형식 400(기간 4개 밖, 사유 없음·200자 초과) → 관리자·탈퇴 유예 400 → 이미 정지 409
 * → 세션 삭제 실패 503.
 */
@Service
public class AdminMemberService {

    /** 정지 이력 최대 개수 (contracts {@code history maxItems}). */
    static final int HISTORY_LIMIT = 50;

    private final ModerationAccess access;
    private final MemberQueryService members;
    private final SuspensionService suspensions;
    private final ReportCaseRepository cases;
    private final ModerationProperties properties;
    private final Clock clock;

    public AdminMemberService(
            ModerationAccess access,
            MemberQueryService members,
            SuspensionService suspensions,
            ReportCaseRepository cases,
            ModerationProperties properties,
            Clock clock) {
        this.access = access;
        this.members = members;
        this.suspensions = suspensions;
        this.cases = cases;
        this.properties = properties;
        this.clock = clock;
    }

    public AdminMemberView view(Viewer viewer, String handle) {
        access.requireAdmin(viewer);
        return view(find(handle));
    }

    public AdminMemberView suspend(
            Viewer viewer, String handle, Object rawDuration, Object rawReason) {
        long admin = access.requireAdmin(viewer);
        AdminMemberInfo member = find(handle);
        List<FieldError> errors = new ArrayList<>();
        SuspensionDuration duration = parseDuration(rawDuration, errors);
        String reason = parseReason(rawReason, errors);
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        suspensions.suspend(member.id(), reason, duration, admin, clock.instant());
        return view(find(handle));
    }

    public AdminMemberView lift(Viewer viewer, String handle) {
        long admin = access.requireAdmin(viewer);
        AdminMemberInfo member = find(handle);
        suspensions.lift(member.id(), admin, clock.instant());
        return view(find(handle));
    }

    private AdminMemberInfo find(String handle) {
        return members.findAdminView(handle).orElseThrow(() -> new NotFoundException("관리자 회원: 없음"));
    }

    private AdminMemberView view(AdminMemberInfo member) {
        List<SuspensionRecord> history = suspensions.history(member.id(), HISTORY_LIMIT);
        SuspensionRecord open =
                history.stream().filter(SuspensionRecord::isOpen).findFirst().orElse(null);
        return new AdminMemberView(
                member.handle(),
                member.nickname(),
                member.role(),
                member.status(),
                member.createdAt(),
                cases.hiddenCountOfAuthor(member.id()),
                open,
                history);
    }

    private SuspensionDuration parseDuration(Object raw, List<FieldError> errors) {
        if (raw == null) {
            errors.add(new FieldError("duration", "REQUIRED", "정지 기간을 골라 주세요"));
            return null;
        }
        SuspensionDuration duration =
                CaseResolutionService.enumOrNull(SuspensionDuration.class, raw);
        if (duration == null || !properties.suspensionDurations().contains(duration)) {
            errors.add(new FieldError("duration", "INVALID_VALUE", "입력한 내용을 확인해 주세요"));
            return null;
        }
        return duration;
    }

    private String parseReason(Object raw, List<FieldError> errors) {
        String text = raw instanceof String s ? s.strip() : null;
        if (text == null || text.isEmpty()) {
            errors.add(new FieldError("reason", "REQUIRED", "정지 사유를 적어 주세요"));
            return null;
        }
        int max = properties.suspensionReasonMaxChars();
        if (text.codePointCount(0, text.length()) > max) {
            errors.add(new FieldError("reason", "TOO_LONG", "사유는 " + max + "자까지 쓸 수 있어요"));
            return null;
        }
        return text;
    }
}
