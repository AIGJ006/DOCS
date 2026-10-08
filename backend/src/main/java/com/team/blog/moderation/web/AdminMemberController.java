package com.team.blog.moderation.web;

import com.team.blog.moderation.application.AdminMemberService;
import com.team.blog.moderation.application.AdminMemberView;
import com.team.blog.moderation.web.dto.SuspendRequest;
import com.team.blog.shared.security.LoginRequired;
import com.team.blog.shared.security.Viewer;
import com.team.blog.shared.web.CacheControlPolicy;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 회원 정지 API (014 T053, contracts {@code getAdminMember}·{@code suspendMember}·{@code
 * liftSuspension}).
 */
@RestController
public class AdminMemberController {

    private final AdminMemberService service;

    public AdminMemberController(AdminMemberService service) {
        this.service = service;
    }

    @LoginRequired
    @GetMapping("/api/admin/members/{handle}")
    public ResponseEntity<AdminMemberView> view(
            Viewer viewer, @PathVariable("handle") String handle) {
        return AdminReportController.ok(service.view(viewer, handle));
    }

    @LoginRequired
    @PostMapping("/api/admin/members/{handle}/suspensions")
    public ResponseEntity<AdminMemberView> suspend(
            Viewer viewer,
            @PathVariable("handle") String handle,
            @RequestBody(required = false) SuspendRequest request) {
        SuspendRequest body = request == null ? new SuspendRequest(null, null) : request;
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.CACHE_CONTROL, CacheControlPolicy.NO_STORE)
                .body(service.suspend(viewer, handle, body.duration(), body.reason()));
    }

    @LoginRequired
    @DeleteMapping("/api/admin/members/{handle}/suspensions/current")
    public ResponseEntity<AdminMemberView> lift(
            Viewer viewer, @PathVariable("handle") String handle) {
        return AdminReportController.ok(service.lift(viewer, handle));
    }
}
