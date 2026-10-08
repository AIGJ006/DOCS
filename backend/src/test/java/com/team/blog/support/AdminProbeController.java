package com.team.blog.support;

import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 004 관리자 경로 확인용 테스트 컨트롤러 (test 프로필 전용, T063). 014 관리자 API가 생기기 전에 "있는 관리자 경로"를 만든다 — 없는 경로와 응답이
 * 같아야 존재가 드러나지 않는다.
 */
@Profile("test")
@RestController
public class AdminProbeController {

    @GetMapping("/api/admin/__probe")
    public Map<String, Object> probe() {
        return Map.of("admin", true);
    }

    @PostMapping("/api/admin/__probe")
    public Map<String, Object> post() {
        return Map.of("admin", true);
    }
}
