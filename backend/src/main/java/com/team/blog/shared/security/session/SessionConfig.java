package com.team.blog.shared.security.session;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisIndexedHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * Spring Session Data Redis <b>인덱스 저장소</b> (R-03). principal 이름(= 회원 번호)으로 그 회원의 모든 세션을 찾을 수 있어
 * 비밀번호 변경·재설정, 정지(014), 탈퇴(015) 때 {@code SessionTerminator}가 세션을 지운다.
 *
 * <ul>
 *   <li>유지 기간: 마지막 활동부터 {@code blog.auth.session-timeout}(기본 14일, 07 L-6).
 *   <li>쿠키: {@code SESSION}, HttpOnly, Secure, SameSite=Lax, Path=/ ({@code
 *       server.servlet.session.cookie.*}와 같은 값).
 *   <li>{@code SessionRepositoryFilter}는 {@link ResilientSessionRepository}(@Primary)를 쓴다 — Redis
 *       장애 시 비로그인 처리.
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableRedisIndexedHttpSession
public class SessionConfig {

    public static final String SESSION_COOKIE_NAME = "SESSION";

    @Bean
    SessionRepositoryCustomizer<RedisIndexedSessionRepository> sessionTimeoutCustomizer(
            @Value("${blog.auth.session-timeout:14d}") Duration sessionTimeout) {
        return repository -> repository.setDefaultMaxInactiveInterval(sessionTimeout);
    }

    @Bean
    CookieSerializer cookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName(SESSION_COOKIE_NAME);
        serializer.setUseHttpOnlyCookie(true);
        serializer.setUseSecureCookie(true);
        serializer.setSameSite("Lax");
        serializer.setCookiePath("/");
        return serializer;
    }

    @Bean
    @Primary
    ResilientSessionRepository<RedisIndexedSessionRepository.RedisSession>
            resilientSessionRepository(RedisIndexedSessionRepository sessionRepository) {
        return new ResilientSessionRepository<>(sessionRepository);
    }
}
