package com.team.blog.interaction.web;

import com.team.blog.interaction.application.LikeService;
import com.team.blog.interaction.application.LikeService.LikeState;
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
 * 좋아요 지정·취소 (009 T016, contracts {@code likePost}·{@code unlikePost}). 응답은 {@code 200 {liked,
 * likeCount}}, 항상 {@code Cache-Control: private, no-store}.
 *
 * <p>현재 사용자는 세션에서 만든 {@link Viewer}로만 받는다. 글 번호가 숫자가 아니거나 1 미만이어도 판정 순서(로그인 → 계정 상태 → 대상)를 지키도록
 * 문자열로 받아 없는 글(404)로 넘긴다. CSRF는 001 공통 설정({@code X-XSRF-TOKEN}).
 */
@RestController
public class LikeController {

    private final LikeService likeService;

    public LikeController(LikeService likeService) {
        this.likeService = likeService;
    }

    @LoginRequired
    @PutMapping("/api/posts/{postId}/like")
    public ResponseEntity<LikeState> like(Viewer viewer, @PathVariable("postId") String postId) {
        return ok(likeService.like(viewer, parseId(postId)));
    }

    @LoginRequired
    @DeleteMapping("/api/posts/{postId}/like")
    public ResponseEntity<LikeState> unlike(Viewer viewer, @PathVariable("postId") String postId) {
        return ok(likeService.unlike(viewer, parseId(postId)));
    }

    private static ResponseEntity<LikeState> ok(LikeState state) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, CacheControlPolicy.NO_STORE)
                .body(state);
    }

    /** 숫자가 아니거나 범위 밖이면 0 — 어떤 글과도 맞지 않아 404가 된다. */
    static long parseId(String raw) {
        try {
            long id = Long.parseLong(raw);
            return id >= 1 ? id : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
