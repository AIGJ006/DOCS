package com.team.blog.discovery.web;

import com.team.blog.discovery.application.CursorPage;
import com.team.blog.discovery.application.PostCardView;
import com.team.blog.discovery.application.trending.TrendingQueryService;
import com.team.blog.shared.web.CacheControlPolicy;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 트렌딩 글 목록 {@code GET /api/posts/trending} (012 T028, openapi {@code listTrendingPosts}). 비회원도 본다.
 * 응답은 005 {@code PostCardPage}와 같은 모양이고 {@code size}는 받지 않는다(9 고정). 410 {@code SNAPSHOT_EXPIRED}면
 * 화면이 안내 후 커서 없이 다시 부른다.
 */
@RestController
public class TrendingController {

    private final TrendingQueryService trending;

    public TrendingController(TrendingQueryService trending) {
        this.trending = trending;
    }

    @GetMapping("/api/posts/trending")
    public ResponseEntity<CursorPage<PostCardView>> listTrendingPosts(
            @RequestParam(required = false) String cursor) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, CacheControlPolicy.NO_CACHE)
                .body(trending.page(cursor));
    }
}
