package com.team.blog.discovery.web;

import com.team.blog.discovery.application.BlogHeaderView;
import com.team.blog.discovery.application.BlogQueryService;
import com.team.blog.discovery.application.CursorPage;
import com.team.blog.discovery.application.PostCardView;
import com.team.blog.shared.security.Viewer;
import com.team.blog.shared.web.CacheControlPolicy;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 개인 블로그 머리말·글 목록 (005 T049, contracts {@code getBlogHeader}·{@code listBlogPosts}).
 *
 * <ul>
 *   <li>{@code tag}(008): 정규화된 태그 이름이면 그 태그 글만, 아니면 404.
 *   <li>{@code size}는 받아도 무시한다 — 서버가 설정값으로 고정(원칙 VII).
 *   <li>API는 대문자 주소를 리다이렉트하지 않고 404다(research R-23) — 301은 화면 경로({@link PageShellController})만 한다.
 *   <li>두 응답 모두 {@code Cache-Control: private, no-cache}(research R-31).
 * </ul>
 *
 * 001이 뒤에 더하는 {@code GET /api/members/{handle}/friend}는 더 구체적인 경로라 아래 매핑과 겹치지 않는다.
 */
@RestController
public class BlogController {

    private final BlogQueryService blogQueryService;

    public BlogController(BlogQueryService blogQueryService) {
        this.blogQueryService = blogQueryService;
    }

    @GetMapping("/api/members/{handle}")
    public ResponseEntity<BlogHeaderView> getBlogHeader(
            @PathVariable String handle, Viewer viewer) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, CacheControlPolicy.NO_CACHE)
                .body(blogQueryService.getHeader(handle, viewer));
    }

    @GetMapping("/api/members/{handle}/posts")
    public ResponseEntity<CursorPage<PostCardView>> listBlogPosts(
            @PathVariable String handle,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String size,
            Viewer viewer) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, CacheControlPolicy.NO_CACHE)
                .body(blogQueryService.listPosts(handle, tag, cursor, viewer));
    }
}
