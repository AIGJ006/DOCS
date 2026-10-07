package com.team.blog.support;

import com.team.blog.shared.security.CurrentUser;
import com.team.blog.shared.security.LoginRequired;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 보안 기반 확인용 테스트 컨트롤러 (test 프로필 전용). */
@Profile("test")
@RestController
public class ProbeController {

    /** 로그인 필요 (/api/me/** 규칙). */
    @GetMapping("/api/me/__probe")
    public Map<String, Object> me(@CurrentUser Long memberId) {
        return Map.of("memberId", memberId);
    }

    /** 요청 본문의 memberId는 무시하고 세션의 회원 번호만 쓴다. */
    @PostMapping("/api/me/__probe")
    public Map<String, Object> meWrite(
            @CurrentUser Long memberId, @RequestBody(required = false) Map<String, Object> body) {
        return Map.of("memberId", memberId);
    }

    /** 누구나 (로그인 상태만 알려 준다). */
    @GetMapping("/api/__public-probe")
    public Map<String, Object> publicProbe(@CurrentUser(required = false) Optional<Long> memberId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("loggedIn", memberId.isPresent());
        body.put("memberId", memberId.orElse(null));
        return body;
    }

    /** 상태를 바꾸는 공개 요청 (CSRF 확인용). */
    @PostMapping("/api/__public-probe")
    public Map<String, Object> publicWrite() {
        return Map.of("ok", true);
    }

    /** 탈퇴 유예 필터(T042a)가 요청 attribute에 둔 회원 정보. */
    @GetMapping("/api/me/__access-info")
    public Object accessInfo(jakarta.servlet.http.HttpServletRequest request) {
        return request.getAttribute(
                com.team.blog.account.application.MemberAccessInfo.REQUEST_ATTRIBUTE);
    }

    /** /api/me 밖이지만 @LoginRequired로 로그인을 요구한다. */
    @LoginRequired
    @GetMapping("/api/__login-required-probe")
    public Map<String, Object> loginRequired(@CurrentUser Long memberId) {
        return Map.of("memberId", memberId);
    }
}
