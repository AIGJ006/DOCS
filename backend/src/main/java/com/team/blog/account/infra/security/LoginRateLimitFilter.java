package com.team.blog.account.infra.security;

import com.team.blog.account.application.AccountReasonCode;
import com.team.blog.account.infra.AccountProperties;
import com.team.blog.account.infra.redis.LoginFailureCounter;
import com.team.blog.shared.error.ErrorResponseWriter;
import com.team.blog.shared.error.TemporarilyUnavailableException;
import com.team.blog.shared.error.TooManyRequestsException;
import com.team.blog.shared.infra.ratelimit.RateLimitResult;
import com.team.blog.shared.infra.ratelimit.RateLimiter;
import com.team.blog.shared.infra.redis.RedisGuard;
import com.team.blog.shared.web.ClientIp;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.OptionalLong;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 이메일 로그인 요청 제한 (FR-035·037, R-12·R-13). {@code POST /api/auth/login}에만, 인증 필터 <b>앞</b>에서 순서대로:
 *
 * <ol>
 *   <li>Redis 장애 → 503 {@code TEMPORARILY_UNAVAILABLE} + {@code Retry-After: 30}. 로그인 세션을 저장할 수
 *       없으므로 비밀번호를 확인하지 않는다(02 §2-1).
 *   <li>{@code rl:login:ip:{ClientIp}} 1분 {@code ip-limit-per-minute}(20)번 초과 → 429 {@code
 *       TOO_MANY_REQUESTS}. IP는 {@link ClientIp}(신뢰 프록시 밖 {@code X-Forwarded-For} 무시).
 *   <li>그 이메일이 잠겨 있음 → 429 {@code LOGIN_TEMPORARILY_LOCKED} + {@code Retry-After}(남은 초). 비밀번호가 맞아도
 *       거부하고, 가입 여부와 무관하게 같은 응답이다.
 * </ol>
 *
 * 실패 횟수는 {@link JsonLoginFailureHandler}가 세고 {@link JsonLoginSuccessHandler}가 지운다. Bean이 아니다 — 서블릿
 * 필터로 따로 등록되지 않도록 {@link AccountSecurityCustomizer}가 만들어 붙인다.
 */
public class LoginRateLimitFilter extends OncePerRequestFilter {

    static final String IP_KEY = "rl:login:ip:";
    private static final Duration IP_WINDOW = Duration.ofMinutes(1);

    private final RedisGuard redisGuard;
    private final RateLimiter rateLimiter;
    private final LoginFailureCounter failureCounter;
    private final ErrorResponseWriter errorWriter;
    private final int ipLimitPerMinute;

    public LoginRateLimitFilter(
            RedisGuard redisGuard,
            RateLimiter rateLimiter,
            LoginFailureCounter failureCounter,
            ErrorResponseWriter errorWriter,
            AccountProperties properties) {
        this.redisGuard = redisGuard;
        this.rateLimiter = rateLimiter;
        this.failureCounter = failureCounter;
        this.errorWriter = errorWriter;
        this.ipLimitPerMinute = properties.auth().login().ipLimitPerMinute();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equals(request.getMethod())
                && AccountSecurityCustomizer.LOGIN_URL.equals(path(request)));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!redisGuard.isAvailable()) {
            errorWriter.write(response, new TemporarilyUnavailableException());
            return;
        }
        if (rateLimiter.tryAcquire(IP_KEY + ClientIp.of(request), ipLimitPerMinute, IP_WINDOW)
                instanceof RateLimitResult.Denied denied) {
            errorWriter.write(response, new TooManyRequestsException(denied.retryAfterSeconds()));
            return;
        }
        OptionalLong locked = failureCounter.lockedFor(request.getParameter("email"));
        if (locked.isPresent()) {
            errorWriter.write(
                    response,
                    new TooManyRequestsException(
                            AccountReasonCode.LOGIN_TEMPORARILY_LOCKED, locked.getAsLong()));
            return;
        }
        chain.doFilter(request, response);
    }

    private static String path(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        return context != null && !context.isEmpty() && uri.startsWith(context)
                ? uri.substring(context.length())
                : uri;
    }
}
