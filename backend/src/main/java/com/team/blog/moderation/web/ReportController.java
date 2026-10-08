package com.team.blog.moderation.web;

import com.team.blog.moderation.application.ReportService;
import com.team.blog.moderation.web.dto.ReportAccepted;
import com.team.blog.moderation.web.dto.ReportRequest;
import com.team.blog.shared.security.LoginRequired;
import com.team.blog.shared.security.Viewer;
import com.team.blog.shared.web.CacheControlPolicy;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 글·댓글 신고 (014 T022, contracts {@code createReport}). 현재 사용자는 세션의 {@link Viewer}로만 받는다. CSRF는 001
 * 공통 설정({@code X-XSRF-TOKEN}).
 */
@RestController
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @LoginRequired
    @PostMapping("/api/reports")
    public ResponseEntity<ReportAccepted> report(
            Viewer viewer, @RequestBody(required = false) ReportRequest request) {
        ReportRequest body = request == null ? new ReportRequest(null, null, null, null) : request;
        reportService.report(
                viewer, body.targetType(), body.targetId(), body.reason(), body.detail());
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, CacheControlPolicy.NO_STORE)
                .body(ReportAccepted.ACCEPTED);
    }
}
