package com.team.blog.tag.web;

import com.team.blog.shared.security.CurrentUser;
import com.team.blog.shared.web.CacheControlPolicy;
import com.team.blog.tag.application.suggest.TagSuggestRequest;
import com.team.blog.tag.application.suggest.TagSuggestService;
import com.team.blog.tag.web.dto.TagSuggestResponse;
import com.team.blog.tag.web.dto.TagSuggestStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 태그 추천 API (013 T028, contracts/openapi.yaml). 현재 사용자는 세션에서만 얻는다(없으면 401). 글 번호는 문자열로 받아 숫자가
 * 아니면 404다(400이 아님). 본문 길이 검사는 판정 순서(404 → 503 → 409 → 400)를 지키려고 {@code @Valid}가 아니라 Service가 한다.
 */
@RestController
public class TagSuggestionController {

    private final TagSuggestService service;

    public TagSuggestionController(@Qualifier("aiTagSuggestService") TagSuggestService service) {
        this.service = service;
    }

    @PostMapping("/api/posts/{postId}/tag-suggestions")
    public ResponseEntity<TagSuggestResponse> suggest(
            @CurrentUser Long memberId,
            @PathVariable String postId,
            @RequestBody(required = false) TagSuggestRequest body) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, CacheControlPolicy.NO_STORE)
                .body(TagSuggestResponse.from(service.suggest(memberId, postId, body)));
    }

    @GetMapping("/api/posts/{postId}/tag-suggestions/status")
    public ResponseEntity<TagSuggestStatus> status(
            @CurrentUser Long memberId, @PathVariable String postId) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, CacheControlPolicy.NO_CACHE)
                .body(TagSuggestStatus.from(service.status(memberId, postId)));
    }
}
