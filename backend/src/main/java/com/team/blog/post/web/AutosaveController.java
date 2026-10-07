package com.team.blog.post.web;

import com.team.blog.post.application.AutosaveService;
import com.team.blog.post.web.dto.SaveRequest;
import com.team.blog.post.web.dto.SaveResponse;
import com.team.blog.shared.security.CurrentUser;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 자동 저장 (contracts {@code autosave}). 429는 {@code Retry-After}, Redis 메모리 부족은 503 {@code
 * AUTOSAVE_UNAVAILABLE}(공통 오류 처리기가 예외의 헤더·코드로 만든다). 1MB 초과는 {@link AutosaveRequestSizeFilter}가 먼저
 * 413으로 거부한다.
 */
@RestController
public class AutosaveController {

    private final AutosaveService autosaveService;

    public AutosaveController(AutosaveService autosaveService) {
        this.autosaveService = autosaveService;
    }

    @PutMapping("/api/posts/{postId}/autosave")
    public SaveResponse autosave(
            @CurrentUser Long memberId,
            @PathVariable long postId,
            @RequestBody SaveRequest request) {
        return SaveResponse.from(
                autosaveService.autosave(
                        postId,
                        memberId,
                        request.title(),
                        request.contentMd(),
                        request.baseVersionOrZero()));
    }
}
