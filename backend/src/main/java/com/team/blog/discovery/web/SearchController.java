package com.team.blog.discovery.web;

import com.team.blog.discovery.application.search.PeopleSearchResult;
import com.team.blog.discovery.application.search.PeopleSearchService;
import com.team.blog.discovery.application.search.PostSearchPage;
import com.team.blog.discovery.application.search.PostSearchService;
import com.team.blog.discovery.application.search.SearchRateLimit;
import com.team.blog.interaction.web.VisitorIdCookieFilter;
import com.team.blog.shared.security.Viewer;
import com.team.blog.shared.web.CacheControlPolicy;
import com.team.blog.shared.web.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 검색 API (012 T016·T034·T035·T036, openapi {@code searchPosts}·{@code searchPeople}). 비회원도 쓴다. 판정
 * 순서는 403(001 게이트) → 404(블로그 안 검색의 블로그) → 400(검색어·정렬·커서) → 429(맨 끝). {@code q}가 없으면 빈 검색어와 같은 400
 * {@code SEARCH_QUERY_TOO_SHORT}. 응답은 {@code Cache-Control: private, no-cache}. {@code q}는 001
 * {@code SensitiveParamMasking}이 접근 로그에서 가린다(FR-039).
 */
@RestController
public class SearchController {

    private final PostSearchService postSearch;
    private final PeopleSearchService peopleSearch;
    private final SearchRateLimit rateLimit;

    public SearchController(
            PostSearchService postSearch,
            PeopleSearchService peopleSearch,
            SearchRateLimit rateLimit) {
        this.postSearch = postSearch;
        this.peopleSearch = peopleSearch;
        this.rateLimit = rateLimit;
    }

    @GetMapping("/api/search/posts")
    public ResponseEntity<PostSearchPage> searchPosts(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String blog,
            @CookieValue(name = VisitorIdCookieFilter.COOKIE_NAME, required = false) String vid,
            Viewer viewer,
            HttpServletRequest request) {
        SearchRateLimit.Visitor visitor = visitor(viewer, vid, request);
        PostSearchPage page =
                postSearch.search(q, sort, cursor, blog, () -> rateLimit.acquire(visitor));
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, CacheControlPolicy.NO_CACHE)
                .body(page);
    }

    @GetMapping("/api/search/people")
    public ResponseEntity<PeopleSearchResult> searchPeople(
            @RequestParam(required = false) String q,
            @CookieValue(name = VisitorIdCookieFilter.COOKIE_NAME, required = false) String vid,
            Viewer viewer,
            HttpServletRequest request) {
        SearchRateLimit.Visitor visitor = visitor(viewer, vid, request);
        PeopleSearchResult result = peopleSearch.search(q, () -> rateLimit.acquire(visitor));
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, CacheControlPolicy.NO_CACHE)
                .body(result);
    }

    private static SearchRateLimit.Visitor visitor(
            Viewer viewer, String vid, HttpServletRequest request) {
        return new SearchRateLimit.Visitor(
                viewer, vid, ClientIp.of(request), request.getHeader(HttpHeaders.USER_AGENT));
    }
}
