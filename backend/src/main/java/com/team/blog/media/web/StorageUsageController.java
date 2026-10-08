package com.team.blog.media.web;

import com.team.blog.media.application.StorageUsage;
import com.team.blog.media.application.StorageUsageQuery;
import com.team.blog.shared.security.CurrentUser;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 사진 저장 공간 (003 T060, contracts/openapi.yaml {@code getMyStorageUsage}). {@code /api/me/**}라 로그인만
 * 받는다.
 */
@RestController
public class StorageUsageController {

    private final StorageUsageQuery query;

    public StorageUsageController(StorageUsageQuery query) {
        this.query = query;
    }

    @GetMapping("/api/me/storage")
    public ResponseEntity<StorageUsage> usage(@CurrentUser Long memberId) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(query.usageOf(memberId));
    }
}
