package com.team.blog.account.infra.security;

import com.team.blog.account.application.AccountReasonCode;
import com.team.blog.shared.error.ErrorResponse;
import com.team.blog.shared.infra.redis.RedisGuard;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 소셜 콜백 실패: {@code state} 불일치·대기 요청 없음, 제공자 오류, Google 이메일 미확인. 오류 본문을 세션에 한 번 보관하고 {@code
 * /login?error=social}로 302. Redis 장애라 세션을 쓸 수 없으면 {@code /login?error=unavailable}("잠시 후 다시 시도해
 * 주세요", R-30)로 보낸다.
 */
@Component
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    public static final String UNAVAILABLE_PAGE = "/login?error=unavailable";

    private static final Logger log = LoggerFactory.getLogger(OAuth2LoginFailureHandler.class);

    private final RedisGuard redisGuard;
    private final JsonMapper jsonMapper;

    public OAuth2LoginFailureHandler(RedisGuard redisGuard, JsonMapper jsonMapper) {
        this.redisGuard = redisGuard;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception)
            throws IOException {
        if (!redisGuard.isAvailable()) {
            response.sendRedirect(UNAVAILABLE_PAGE);
            return;
        }
        AccountReasonCode reason = AccountReasonCode.SOCIAL_LOGIN_FAILED;
        if (exception instanceof OAuth2AuthenticationException oauth) {
            log.info("소셜 로그인 실패: {}", oauth.getError().getErrorCode());
            if (OidcMemberUserService.EMAIL_NOT_VERIFIED.equals(oauth.getError().getErrorCode())) {
                reason = AccountReasonCode.SOCIAL_EMAIL_NOT_VERIFIED;
            }
        }
        var session = request.getSession(true);
        session.removeAttribute(SocialLoginSession.PENDING_SIGNUP);
        session.setAttribute(
                SocialLoginSession.LOGIN_ERROR,
                jsonMapper.writeValueAsString(ErrorResponse.of(reason)));
        response.sendRedirect(OAuth2LoginSuccessHandler.ERROR_PAGE);
    }
}
