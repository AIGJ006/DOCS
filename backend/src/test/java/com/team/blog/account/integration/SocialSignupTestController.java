package com.team.blog.account.integration;

import com.team.blog.account.application.PendingSocialSignup;
import com.team.blog.account.infra.security.SocialLoginSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.time.Duration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * test 프로필 전용: 세션에 보관된 소셜 가입 대기 정보의 {@code createdAt}을 과거로 옮긴다({@code POST
 * /test/social-signup/age/{minutes}}). {@code Clock} Bean을 바꾸는 새 컨텍스트를 만들지 않고 10분 만료를 확인하기 위해서다.
 */
@Profile("test")
@RestController
public class SocialSignupTestController {

    @PostMapping("/test/social-signup/age/{minutes}")
    public ResponseEntity<Void> age(@PathVariable long minutes, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null
                || !(session.getAttribute(SocialLoginSession.PENDING_SIGNUP)
                        instanceof PendingSocialSignup pending)) {
            return ResponseEntity.notFound().build();
        }
        session.setAttribute(
                SocialLoginSession.PENDING_SIGNUP,
                pending.withCreatedAt(pending.createdAt().minus(Duration.ofMinutes(minutes))));
        return ResponseEntity.noContent().build();
    }
}
