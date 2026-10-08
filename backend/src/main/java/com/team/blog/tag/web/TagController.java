package com.team.blog.tag.web;

import com.team.blog.discovery.application.CursorPage;
import com.team.blog.discovery.application.PostCardView;
import com.team.blog.discovery.application.TagPostQueryService;
import com.team.blog.shared.web.CacheControlPolicy;
import com.team.blog.tag.application.TagQueryService;
import com.team.blog.tag.application.TagSummaryView;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 태그 API (008 contracts/openapi.yaml). 머리말과 목록은 두 구간 경로({@code /summary}·{@code /posts})라 자동완성
 * {@code /api/tags/suggest}와 겹치지 않는다(research R7).
 *
 * <ul>
 *   <li>경로의 이름은 Spring이 퍼센트 디코딩한 값이다({@code c%23} → {@code c#}). 정규화된 이름이 아니면 404.
 *   <li>{@code size}·{@code limit}은 받아도 무시한다(원칙 VII).
 *   <li>응답 {@code Cache-Control: private, no-cache}(005 R-31). SecurityConfig는 {@code /api/me/**}만
 *       막으므로 따로 고치지 않는다.
 * </ul>
 */
@RestController
public class TagController {

    private final TagQueryService tagQueryService;
    private final TagPostQueryService tagPostQueryService;

    public TagController(TagQueryService tagQueryService, TagPostQueryService tagPostQueryService) {
        this.tagQueryService = tagQueryService;
        this.tagPostQueryService = tagPostQueryService;
    }

    @GetMapping("/api/tags/{name}/summary")
    public ResponseEntity<TagSummaryView> summary(@PathVariable String name) {
        return ok(tagQueryService.summary(name));
    }

    @GetMapping("/api/tags/{name}/posts")
    public ResponseEntity<CursorPage<PostCardView>> posts(
            @PathVariable String name,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String size) {
        return ok(tagPostQueryService.page(name, cursor));
    }

    private static <T> ResponseEntity<T> ok(T body) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, CacheControlPolicy.NO_CACHE)
                .body(body);
    }
}
