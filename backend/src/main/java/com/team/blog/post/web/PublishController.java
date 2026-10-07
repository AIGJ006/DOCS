package com.team.blog.post.web;

import com.team.blog.post.application.PublishService;
import com.team.blog.post.web.dto.PublishRequest;
import com.team.blog.post.web.dto.PublishResponse;
import com.team.blog.shared.security.CurrentUser;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * 발행·다시 발행 (contracts {@code publishPost}). {@code Idempotency-Key} 헤더는 필수이며 형식 확인은 계정 상태·소유 판정 뒤에
 * 한다(42 §3 순서). 같은 키의 중복 판정(002 T111): 처리 중이면 409 {@code IN_PROGRESS}, 끝났으면 저장된 응답(200), 다른 내용·다른
 * 글이면 422 {@code IDEMPOTENCY_KEY_REUSED}. Redis 장애면 건너뛰고 행 잠금 + 버전 확인으로 막는다.
 */
@RestController
public class PublishController {

    private final PublishService publishService;

    public PublishController(PublishService publishService) {
        this.publishService = publishService;
    }

    @PostMapping("/api/posts/{postId}/publish")
    public PublishResponse publish(
            @CurrentUser Long memberId,
            @PathVariable long postId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody PublishRequest request) {
        return PublishResponse.from(
                publishService.publish(
                        postId,
                        memberId,
                        request.title(),
                        request.contentMd(),
                        request.tags(),
                        request.visibility(),
                        request.baseVersion() == null ? 0 : request.baseVersion(),
                        idempotencyKey));
    }
}
