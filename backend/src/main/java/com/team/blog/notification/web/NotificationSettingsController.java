package com.team.blog.notification.web;

import com.team.blog.notification.application.NotificationSettingsService;
import com.team.blog.shared.security.LoginRequired;
import com.team.blog.shared.security.Viewer;
import com.team.blog.shared.web.CacheControlPolicy;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 알림 설정 {@code GET}·{@code PUT /api/me/notification-settings} (011 US6). 본문은 5개 키를 모두 boolean으로 받는다
 * — 빠진 키·boolean이 아닌 값·모르는 키는 400 {@code VALIDATION_FAILED}(칸별 오류). 칸별로 모아 알려 주려고 record 대신 맵으로 받아
 * Service가 검사한다.
 */
@RestController
public class NotificationSettingsController {

    private final NotificationSettingsService settings;

    public NotificationSettingsController(NotificationSettingsService settings) {
        this.settings = settings;
    }

    @LoginRequired
    @GetMapping("/api/me/notification-settings")
    public ResponseEntity<Map<String, Boolean>> get(Viewer viewer) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, CacheControlPolicy.NO_STORE)
                .body(settings.get(viewer.id()));
    }

    @LoginRequired
    @PutMapping("/api/me/notification-settings")
    public ResponseEntity<Map<String, Boolean>> put(
            Viewer viewer, @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, CacheControlPolicy.NO_STORE)
                .body(settings.put(viewer.id(), body));
    }
}
