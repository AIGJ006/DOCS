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
 * 한다(42 §3 순서). 같은 키의 중복 판정은 US6(T111)에서 붙인다.
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
