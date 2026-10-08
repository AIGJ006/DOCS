package com.team.blog.moderation.web;

import com.team.blog.moderation.application.CaseDetail;
import com.team.blog.moderation.application.CasePage;
import com.team.blog.moderation.application.CaseQueryService;
import com.team.blog.moderation.application.CaseResolutionService;
import com.team.blog.moderation.web.dto.ResolutionRequest;
import com.team.blog.shared.error.FieldError;
import com.team.blog.shared.error.NotFoundException;
import com.team.blog.shared.error.ValidationException;
import com.team.blog.shared.security.LoginRequired;
import com.team.blog.shared.security.Viewer;
import com.team.blog.shared.web.CacheControlPolicy;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 신고 사건 API (014 T037, contracts {@code listReportCases}·{@code getReportCase}·{@code
 * resolveReportCase}). {@code /api/admin/**}는 004 관리자 경로 규칙(비회원 401·일반 회원 404)이 먼저 막고, Service가 다시
 * 확인한다. 응답은 항상 {@code Cache-Control: private, no-store}.
 */
@RestController
public class AdminReportController {

    private final CaseQueryService queries;
    private final CaseResolutionService resolutions;

    public AdminReportController(CaseQueryService queries, CaseResolutionService resolutions) {
        this.queries = queries;
        this.resolutions = resolutions;
    }

    @LoginRequired
    @GetMapping("/api/admin/reports")
    public ResponseEntity<CasePage> list(
            Viewer viewer,
            @RequestParam(name = "tab", required = false) String tab,
            @RequestParam(name = "cursor", required = false) String cursor) {
        return ok(queries.list(viewer, tab(tab), cursor));
    }

    @LoginRequired
    @GetMapping("/api/admin/reports/{caseId}")
    public ResponseEntity<CaseDetail> detail(Viewer viewer, @PathVariable("caseId") String caseId) {
        return ok(queries.detail(viewer, existing(caseId)));
    }

    @LoginRequired
    @PostMapping("/api/admin/reports/{caseId}/resolution")
    public ResponseEntity<CaseDetail> resolve(
            Viewer viewer,
            @PathVariable("caseId") String caseId,
            @RequestBody(required = false) ResolutionRequest request) {
        ResolutionRequest body = request == null ? new ResolutionRequest(null, null) : request;
        return ok(resolutions.resolve(viewer, existing(caseId), body.action(), body.reason()));
    }

    private static CaseQueryService.Tab tab(String raw) {
        if (raw == null || raw.isEmpty()) {
            return CaseQueryService.Tab.PENDING;
        }
        try {
            return CaseQueryService.Tab.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new ValidationException(
                    List.of(new FieldError("tab", "INVALID_VALUE", "입력한 내용을 확인해 주세요")));
        }
    }

    /** 숫자가 아닌 번호는 없는 사건과 같은 404. 관리자 확인은 Service가 하므로 0도 그대로 넘겨 404가 되게 한다. */
    private static long existing(String raw) {
        long id = PathIds.parse(raw);
        if (id == 0 && raw != null && raw.isEmpty()) {
            throw new NotFoundException();
        }
        return id;
    }

    static <T> ResponseEntity<T> ok(T body) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, CacheControlPolicy.NO_STORE)
                .body(body);
    }
}
