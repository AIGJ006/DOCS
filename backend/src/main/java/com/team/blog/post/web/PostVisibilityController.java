package com.team.blog.post.web;

import com.team.blog.post.application.PostVisibilityService;
import com.team.blog.post.web.dto.VisibilityChangeRequest;
import com.team.blog.post.web.dto.VisibilityChangeResponse;
import com.team.blog.shared.security.Viewer;
import com.team.blog.shared.web.CacheControlPolicy;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 글 공개 범위 지정 (004 contracts {@code setPostVisibility}, research R-20: 06 §4의 PATCH 대신 O8 규약의 PUT).
 * 다시 발행하지 않고 즉시 바뀐다. 응답은 항상 {@code Cache-Control: private, no-store}.
 *
 * <p>현재 사용자는 {@code CurrentViewerResolver}가 세션에서 만든 {@link Viewer}로만 받는다. 글 번호가 숫자가 아니거나 1 미만이어도 판정
 * 순서(로그인 → 계정 상태 → 대상)를 지키도록 문자열로 받아 없는 글(404)로 넘긴다.
 *
 * <p>(구현 메모) 공통 pom에 springdoc이 없어 OpenAPI 어노테이션은 붙이지 않았다. 계약 일치는 {@code
 * VisibilityOpenApiContractIT}가 계약 YAML과 실제 응답을 견줘 확인한다.
 */
@RestController
public class PostVisibilityController {

    private final PostVisibilityService visibilityService;

    public PostVisibilityController(PostVisibilityService visibilityService) {
        this.visibilityService = visibilityService;
    }

    @PutMapping("/api/posts/{postId}/visibility")
    public ResponseEntity<VisibilityChangeResponse> change(
            Viewer viewer,
            @PathVariable("postId") String postId,
            @RequestBody VisibilityChangeRequest request) {
        VisibilityChangeResponse body =
                VisibilityChangeResponse.from(
                        visibilityService.change(viewer, parseId(postId), request.visibility()));
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, CacheControlPolicy.NO_STORE)
                .body(body);
    }

    /** 숫자가 아니거나 범위 밖이면 0 — 어떤 글과도 맞지 않아 404가 된다. */
    private static long parseId(String raw) {
        try {
            long id = Long.parseLong(raw);
            return id >= 1 ? id : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
