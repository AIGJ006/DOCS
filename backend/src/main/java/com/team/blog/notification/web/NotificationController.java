package com.team.blog.notification.web;

import com.team.blog.notification.application.NotificationCommandService;
import com.team.blog.notification.application.NotificationItem;
import com.team.blog.notification.application.NotificationQueryService;
import com.team.blog.shared.security.LoginRequired;
import com.team.blog.shared.security.Viewer;
import com.team.blog.shared.web.CacheControlPolicy;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 알림 API 5개 (011 contracts openapi). 현재 사용자는 세션의 {@link Viewer}로만 받는다 — 주소·본문에 회원 번호가 없다. 알림 번호가
 * 숫자가 아니면 판정 순서(로그인 → 계정 상태 → 대상)를 지키도록 문자열로 받아 없는 번호(404)로 넘긴다. CSRF는 001 공통 설정.
 */
@RestController
public class NotificationController {

    private final NotificationQueryService queries;
    private final NotificationCommandService commands;

    public NotificationController(
            NotificationQueryService queries, NotificationCommandService commands) {
        this.queries = queries;
        this.commands = commands;
    }

    @LoginRequired
    @GetMapping("/api/notifications/unread-count")
    public ResponseEntity<NotificationItem.UnreadCount> unreadCount(Viewer viewer) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, CacheControlPolicy.NO_STORE)
                .body(new NotificationItem.UnreadCount(queries.unreadCount(viewer.id())));
    }

    @LoginRequired
    @GetMapping("/api/notifications")
    public ResponseEntity<NotificationItem.Page> list(
            Viewer viewer,
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "size", required = false) String size) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, CacheControlPolicy.NO_STORE)
                .body(queries.page(viewer, cursor, parseSize(size)));
    }

    @LoginRequired
    @PutMapping("/api/notifications/{notificationId}/read")
    public ResponseEntity<Void> read(
            Viewer viewer, @PathVariable("notificationId") String notificationId) {
        commands.markRead(viewer.id(), parseId(notificationId));
        return ResponseEntity.noContent().build();
    }

    @LoginRequired
    @PostMapping("/api/notifications/read-all")
    public ResponseEntity<NotificationItem.ReadAllResult> readAll(Viewer viewer) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, CacheControlPolicy.NO_STORE)
                .body(new NotificationItem.ReadAllResult(commands.markAllRead(viewer.id())));
    }

    @LoginRequired
    @DeleteMapping("/api/notifications/{notificationId}")
    public ResponseEntity<Void> delete(
            Viewer viewer, @PathVariable("notificationId") String notificationId) {
        commands.delete(viewer.id(), parseId(notificationId));
        return ResponseEntity.noContent().build();
    }

    /** 숫자가 아니거나 범위 밖이면 0 — 어떤 알림과도 맞지 않아 404가 된다. */
    static long parseId(String raw) {
        try {
            long id = Long.parseLong(raw);
            return id >= 1 ? id : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 숫자가 아니면 -1 — 허용 크기가 아니라 400 {@code VALIDATION_FAILED}. */
    static Integer parseSize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
