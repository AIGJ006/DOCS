package com.team.blog.category.web;

import com.team.blog.category.application.PostCategoryService;
import com.team.blog.shared.security.CurrentUser;
import com.team.blog.shared.web.CacheControlPolicy;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 글의 카테고리 (017 contracts {@code getPostCategory}·{@code setPostCategory}). 발행과 별개로 바로 저장한다(research
 * R5). 글 번호가 숫자가 아니면 판정 순서(로그인 → 계정 상태 → 대상)를 지키도록 없는 글(404)로 넘긴다.
 */
@RestController
public class PostCategoryController {

    private final PostCategoryService service;

    public PostCategoryController(PostCategoryService service) {
        this.service = service;
    }

    /**
     * @param categoryId 분류 없음이면 {@code null}
     */
    public record PostCategoryBody(Long categoryId) {}

    @GetMapping("/api/posts/{postId}/category")
    public ResponseEntity<PostCategoryBody> get(
            @CurrentUser Long memberId, @PathVariable String postId) {
        return noStore(
                new PostCategoryBody(
                        service.current(memberId, MyCategoryController.parseId(postId))));
    }

    @PutMapping("/api/posts/{postId}/category")
    public ResponseEntity<PostCategoryBody> put(
            @CurrentUser Long memberId,
            @PathVariable String postId,
            @RequestBody PostCategoryBody request) {
        return noStore(
                new PostCategoryBody(
                        service.assign(
                                memberId,
                                MyCategoryController.parseId(postId),
                                request.categoryId())));
    }

    private static ResponseEntity<PostCategoryBody> noStore(PostCategoryBody body) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, CacheControlPolicy.NO_STORE)
                .body(body);
    }
}
