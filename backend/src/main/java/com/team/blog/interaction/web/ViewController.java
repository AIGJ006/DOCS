package com.team.blog.interaction.web;

import com.team.blog.interaction.application.ViewRecordService;
import com.team.blog.interaction.application.ViewRecordService.ViewRequest;
import com.team.blog.shared.security.Viewer;
import com.team.blog.shared.web.CacheControlPolicy;
import com.team.blog.shared.web.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 조회 기록 (009 T032, contracts {@code recordPostView}). 비회원도 부를 수 있고, 셌든 중복이든 제외됐든 Redis 장애로 건너뛰었든 모두
 * 같은 {@code 204}(본문 없음, {@code Cache-Control: private, no-store})다. 볼 수 없는 글은 404, 같은 방문자 1분 60번
 * 초과는 429. CSRF는 001 공통 설정({@code X-XSRF-TOKEN}). 화면은 005 {@code useViewBeacon}이 보낸다.
 */
@RestController
public class ViewController {

    private final ViewRecordService viewRecordService;

    public ViewController(ViewRecordService viewRecordService) {
        this.viewRecordService = viewRecordService;
    }

    @PostMapping("/api/posts/{postId}/views")
    public ResponseEntity<Void> record(
            Viewer viewer,
            @PathVariable("postId") String postId,
            @CookieValue(name = VisitorIdCookieFilter.COOKIE_NAME, required = false) String vid,
            HttpServletRequest request) {
        viewRecordService.record(
                viewer,
                LikeController.parseId(postId),
                new ViewRequest(
                        request.getHeader(HttpHeaders.USER_AGENT),
                        request.getHeader("Sec-Purpose"),
                        request.getHeader("Purpose"),
                        vid,
                        ClientIp.of(request)));
        return ResponseEntity.noContent()
                .header(HttpHeaders.CACHE_CONTROL, CacheControlPolicy.NO_STORE)
                .build();
    }
}
