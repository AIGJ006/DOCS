package com.team.blog.tag.web;

import com.team.blog.discovery.application.CursorPage;
import com.team.blog.discovery.application.PostCardView;
import com.team.blog.discovery.application.TagPostQueryService;
import com.team.blog.shared.security.CurrentUser;
import com.team.blog.shared.security.LoginRequired;
import com.team.blog.shared.web.CacheControlPolicy;
import com.team.blog.tag.application.TagIndexView;
import com.team.blog.tag.application.TagQueryService;
import com.team.blog.tag.application.TagSuggestService;
import com.team.blog.tag.application.TagSuggestionView;
import com.team.blog.tag.application.TagSummaryView;
import java.util.List;
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
    private final TagSuggestService tagSuggestService;

    public TagController(
            TagQueryService tagQueryService,
            TagPostQueryService tagPostQueryService,
            TagSuggestService tagSuggestService) {
        this.tagQueryService = tagQueryService;
        this.tagPostQueryService = tagPostQueryService;
        this.tagSuggestService = tagSuggestService;
    }

    /** 전체 태그 목록. {@code limit}은 받아도 무시한다(원칙 VII — 서버 설정값). */
    @GetMapping("/api/tags")
    public ResponseEntity<TagIndexView> top(@RequestParam(required = false) String limit) {
        return ok(tagQueryService.top());
    }

    /** 자동완성 (로그인한 회원만, 이메일 인증 전 허용). 현재 사용자는 세션에서만 얻는다. */
    @LoginRequired
    @GetMapping("/api/tags/suggest")
    public ResponseEntity<List<TagSuggestionView>> suggest(
            @RequestParam(required = false) String q, @CurrentUser Long memberId) {
        return ok(tagSuggestService.suggest(q, memberId));
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
