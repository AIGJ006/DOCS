package com.team.blog.discovery.web;

import com.team.blog.discovery.application.CursorPage;
import com.team.blog.discovery.application.HomeQueryService;
import com.team.blog.discovery.application.PostCardView;
import com.team.blog.shared.security.Viewer;
import com.team.blog.shared.web.CacheControlPolicy;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 전체 글 목록 {@code GET /api/posts} (005 T023, contracts {@code listHomePosts}).
 *
 * <ul>
 *   <li>{@code size}는 받아도 무시한다 — 서버가 설정값으로 고정(10 §4-2, 원칙 VII).
 *   <li>보는 사람은 세션에서만 온다(004 {@code CurrentViewerResolver}).
 *   <li>{@code Cache-Control: private, no-cache}(research R-31). 잘못된 커서는 001 {@code
 *       GlobalExceptionHandler}가 400 {@code INVALID_CURSOR}로 바꾼다.
 * </ul>
 */
@RestController
public class HomePostController {

    private final HomeQueryService homeQueryService;

    public HomePostController(HomeQueryService homeQueryService) {
        this.homeQueryService = homeQueryService;
    }

    @GetMapping("/api/posts")
    public ResponseEntity<CursorPage<PostCardView>> listHomePosts(
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String size,
            Viewer viewer) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, CacheControlPolicy.NO_CACHE)
                .body(homeQueryService.listHome(cursor, viewer));
    }
}
