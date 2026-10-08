package com.team.blog.support;

import com.team.blog.shared.security.MemberPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * test 프로필 전용 로그인: {@code POST /test/login-as/{memberId}}. 비밀번호 없이 그 회원의 {@link MemberPrincipal}로
 * SecurityContext를 만들어 세션(Redis)에 저장한다. 계정 상태와 무관하게 세션을 만든다(남은 세션 시나리오용).
 *
 * <p>001 최근 활동 갱신(LastActiveTouchFilter)의 간격 키 {@code member:active-touch:{id}}를 미리 채워, 로그인 직후 첫
 * 요청이 {@code last_active_at} UPDATE를 더하지 않게 한다(다른 기능의 SQL 수 시험 보호). 갱신을 시험할 때는 그 키를 지운다.
 */
@Profile("test")
@RestController
public class TestLoginController {

    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final HttpSessionSecurityContextRepository contextRepository =
            new HttpSessionSecurityContextRepository();

    public TestLoginController(JdbcTemplate jdbc, StringRedisTemplate redis) {
        this.jdbc = jdbc;
        this.redis = redis;
    }

    @PostMapping("/test/login-as/{memberId}")
    public Map<String, Object> loginAs(
            @PathVariable long memberId, HttpServletRequest request, HttpServletResponse response) {
        String role =
                jdbc.queryForObject("SELECT role FROM member WHERE id = ?", String.class, memberId);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(MemberPrincipal.authenticated(memberId, role));
        SecurityContextHolder.setContext(context);
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        request.getSession(true);
        contextRepository.saveContext(context, request, response);
        redis.opsForValue().set("member:active-touch:" + memberId, "1", Duration.ofHours(1));
        return Map.of("memberId", memberId);
    }
}
