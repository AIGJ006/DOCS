package com.team.blog.interaction.web;

import com.team.blog.interaction.application.FollowQueryService;
import com.team.blog.interaction.application.FollowQueryService.FollowListPage;
import com.team.blog.shared.security.Viewer;
import com.team.blog.shared.web.CacheControlPolicy;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 팔로워·팔로잉 목록 (010 T035, contracts {@code listFollowers}·{@code listFollowing}). 로그인 없이 누구나 본다(F-3).
 *
 * <ul>
 *   <li>{@code size}는 받아도 무시한다 — 서버가 설정값({@code blog.follow.list-page-size})으로 고정(원칙 VII).
 *   <li>없는 주소·탈퇴 유예·익명 처리·대문자 섞인 주소는 같은 404. 다른 목록의 커서는 400 {@code INVALID_CURSOR}.
 *   <li>보는 사람마다 {@code followedByMe}가 달라 {@code Cache-Control: private, no-cache}.
 * </ul>
 */
@RestController
public class FollowListController {

    private final FollowQueryService followQueryService;

    public FollowListController(FollowQueryService followQueryService) {
        this.followQueryService = followQueryService;
    }

    @GetMapping("/api/members/{handle}/followers")
    public ResponseEntity<FollowListPage> followers(
            @PathVariable String handle,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String size,
            Viewer viewer) {
        return ok(followQueryService.followers(handle, cursor, viewer));
    }

    @GetMapping("/api/members/{handle}/following")
    public ResponseEntity<FollowListPage> following(
            @PathVariable String handle,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String size,
            Viewer viewer) {
        return ok(followQueryService.following(handle, cursor, viewer));
    }

    private static ResponseEntity<FollowListPage> ok(FollowListPage page) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, CacheControlPolicy.NO_CACHE)
                .body(page);
    }
}
