package com.team.blog.moderation.web;

import com.team.blog.moderation.application.DirectHideService;
import com.team.blog.moderation.application.HiddenState;
import com.team.blog.moderation.web.dto.HideRequest;
import com.team.blog.shared.event.ReportTargetType;
import com.team.blog.shared.security.LoginRequired;
import com.team.blog.shared.security.Viewer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 직접 숨김·해제 (014 T037·T044·T048, contracts {@code hidePost}·{@code unhidePost}·{@code
 * hideComment}·{@code unhideComment}).
 */
@RestController
public class AdminHideController {

    private final DirectHideService service;

    public AdminHideController(DirectHideService service) {
        this.service = service;
    }

    @LoginRequired
    @PutMapping("/api/admin/posts/{postId}/hidden")
    public ResponseEntity<HiddenState> hidePost(
            Viewer viewer,
            @PathVariable("postId") String postId,
            @RequestBody(required = false) HideRequest request) {
        return AdminReportController.ok(
                service.hide(
                        viewer, ReportTargetType.POST, PathIds.parse(postId), reason(request)));
    }

    @LoginRequired
    @DeleteMapping("/api/admin/posts/{postId}/hidden")
    public ResponseEntity<HiddenState> unhidePost(
            Viewer viewer, @PathVariable("postId") String postId) {
        return AdminReportController.ok(
                service.unhide(viewer, ReportTargetType.POST, PathIds.parse(postId)));
    }

    @LoginRequired
    @PutMapping("/api/admin/comments/{commentId}/hidden")
    public ResponseEntity<HiddenState> hideComment(
            Viewer viewer,
            @PathVariable("commentId") String commentId,
            @RequestBody(required = false) HideRequest request) {
        return AdminReportController.ok(
                service.hide(
                        viewer,
                        ReportTargetType.COMMENT,
                        PathIds.parse(commentId),
                        reason(request)));
    }

    @LoginRequired
    @DeleteMapping("/api/admin/comments/{commentId}/hidden")
    public ResponseEntity<HiddenState> unhideComment(
            Viewer viewer, @PathVariable("commentId") String commentId) {
        return AdminReportController.ok(
                service.unhide(viewer, ReportTargetType.COMMENT, PathIds.parse(commentId)));
    }

    private static Object reason(HideRequest request) {
        return request == null ? null : request.reason();
    }
}
