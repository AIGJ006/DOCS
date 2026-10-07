package com.team.blog.account.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 인증 API. 가입·로그인·인증·재설정 경로는 US1·US4에서 더한다 (contracts/openapi.yaml). */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /**
     * CSRF 토큰 쿠키 발급 ({@code issueCsrfToken}). 앱 첫 진입 때 호출하며 재동의 전·탈퇴 유예 중에도 허용된다. 쿠키는 공통
     * SecurityConfig의 CSRF 필터가 싣는다.
     */
    @GetMapping("/csrf")
    public ResponseEntity<Void> issueCsrfToken() {
        return ResponseEntity.noContent().build();
    }
}
