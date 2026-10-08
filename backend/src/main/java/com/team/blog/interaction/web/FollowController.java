package com.team.blog.interaction.web;

import com.team.blog.interaction.application.FollowService;
import com.team.blog.interaction.application.FollowService.FollowState;
import com.team.blog.shared.security.LoginRequired;
import com.team.blog.shared.security.Viewer;
import com.team.blog.shared.web.CacheControlPolicy;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 팔로우 지정·해제 (010 T018, contracts {@code followMember}·{@code unfollowMember}). 응답은 {@code 200
 * {following, followerCount}}, 항상 {@code Cache-Control: private, no-store}.
 *
 * <p>현재 사용자는 세션에서 만든 {@link Viewer}로만 받는다. 주소는 저장된 값과 정확히 같아야 한다 — 대문자가 섞이면 404(화면 경로는 005 셸이 301).
 * CSRF는 001 공통 설정({@code X-XSRF-TOKEN}).
 */
@RestController
public class FollowController {

    private final FollowService followService;

    public FollowController(FollowService followService) {
        this.followService = followService;
    }

    @LoginRequired
    @PutMapping("/api/members/{handle}/follow")
    public ResponseEntity<FollowState> follow(Viewer viewer, @PathVariable String handle) {
        return ok(followService.follow(viewer, handle));
    }

    @LoginRequired
    @DeleteMapping("/api/members/{handle}/follow")
    public ResponseEntity<FollowState> unfollow(Viewer viewer, @PathVariable String handle) {
        return ok(followService.unfollow(viewer, handle));
    }

    private static ResponseEntity<FollowState> ok(FollowState state) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, CacheControlPolicy.NO_STORE)
                .body(state);
    }
}
