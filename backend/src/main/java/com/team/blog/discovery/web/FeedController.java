package com.team.blog.discovery.web;

import com.team.blog.discovery.application.FeedQueryService;
import com.team.blog.discovery.application.FeedQueryService.FeedPage;
import com.team.blog.shared.security.LoginRequired;
import com.team.blog.shared.security.Viewer;
import com.team.blog.shared.web.CacheControlPolicy;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 팔로잉 피드 {@code GET /api/feed} (010 T028, contracts {@code getFeed}).
 *
 * <ul>
 *   <li>로그인 필요(401). 탈퇴 유예는 001 게이트가 403. 인증 전·남은 세션의 정지 회원도 읽기는 허용한다(가드를 부르지 않음 — 005 홈과 같음).
 *   <li>{@code size}는 받아도 무시한다 — 서버가 005 {@code blog.list.page-size}로 고정(원칙 VII).
 *   <li>보는 사람마다 내용이 달라 {@code Cache-Control: private, no-cache}. 잘못된 커서는 400 {@code
 *       INVALID_CURSOR}.
 * </ul>
 */
@RestController
public class FeedController {

    private final FeedQueryService feedQueryService;

    public FeedController(FeedQueryService feedQueryService) {
        this.feedQueryService = feedQueryService;
    }

    @LoginRequired
    @GetMapping("/api/feed")
    public ResponseEntity<FeedPage> feed(
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String size,
            Viewer viewer) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, CacheControlPolicy.NO_CACHE)
                .body(feedQueryService.page(viewer.id(), cursor));
    }
}
