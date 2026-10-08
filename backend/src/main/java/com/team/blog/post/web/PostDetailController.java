package com.team.blog.post.web;

import com.team.blog.post.application.PostDetailView;
import com.team.blog.post.application.PostQueryService;
import com.team.blog.post.infra.PostDetailRow;
import com.team.blog.shared.security.Viewer;
import com.team.blog.shared.web.CacheControlPolicy;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 글 상세 {@code GET /api/posts/{postId}} (005 T037, contracts {@code getPostDetail}).
 *
 * <ul>
 *   <li>경로 변수는 {@code String}이다 — 숫자가 아니면 400이 아니라 404(FR-026 ②).
 *   <li>보는 사람은 세션에서만 온다(004 {@code CurrentViewerResolver}).
 *   <li>{@code Cache-Control}은 004 {@code CacheControlPolicy.forPost} — 발행·공개·숨김 아님이면 {@code
 *       private, no-cache}, 그 밖(작성자가 보는 비공개·임시·숨김 글)은 {@code private, no-store}.
 *   <li>404는 001 {@code GlobalExceptionHandler}가 공통 본문 + {@code private, no-store}로 낸다.
 * </ul>
 */
@RestController
public class PostDetailController {

    private final PostQueryService postQueryService;

    public PostDetailController(PostQueryService postQueryService) {
        this.postQueryService = postQueryService;
    }

    @GetMapping("/api/posts/{postId}")
    public ResponseEntity<PostDetailView> getPostDetail(
            @PathVariable String postId, Viewer viewer) {
        PostDetailRow row = postQueryService.requireReadableRow(postId, viewer);
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CACHE_CONTROL,
                        CacheControlPolicy.forPost(row.status(), row.visibility(), row.isHidden()))
                .body(postQueryService.detailOf(row, viewer));
    }
}
