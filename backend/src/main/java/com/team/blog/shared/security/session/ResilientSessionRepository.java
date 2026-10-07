package com.team.blog.shared.security.session;

import com.team.blog.shared.error.TemporarilyUnavailableException;
import com.team.blog.shared.infra.redis.RedisGuard;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;

/**
 * Redis 장애에 견디는 세션 저장소 래퍼 (02 §2-1, R-30, README "세션").
 *
 * <ul>
 *   <li>{@link #findById}: Redis 장애면 {@code null} — 그 요청은 비로그인으로 처리되어 공개 읽기는 계속되고 로그인이 필요한 요청은 401.
 *       브라우저의 세션 쿠키는 지우지 않으므로 Redis가 돌아오면 다시 로그인 상태다.
 *   <li>{@link #save}: Redis 장애면 익명 세션은 저장을 건너뛰고(경고 로그), 로그인 처리 중인 세션(인증 정보 또는 소셜 가입 대기 정보가 있음)은
 *       {@link TemporarilyUnavailableException}(503)으로 거부한다 — 새 로그인은 할 수 없다.
 *   <li>{@link #findByIndexNameAndIndexValue}·{@link #deleteById}: 예외를 그대로 올린다. 세션 삭제(비밀번호
 *       변경·정지·탈퇴)는 확인 없이 넘어가면 안 되기 때문이다({@code SessionTerminator}가 503으로 바꾼다).
 * </ul>
 */
public class ResilientSessionRepository<S extends Session>
        implements FindByIndexNameSessionRepository<S> {

    private static final Logger log = LoggerFactory.getLogger(ResilientSessionRepository.class);

    /** 저장하지 못하면 거부해야 하는 세션 속성 (로그인·소셜 가입 진행 중). */
    static final List<String> CRITICAL_ATTRIBUTES =
            List.of(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                    "pendingSocialSignup");

    private final FindByIndexNameSessionRepository<S> delegate;

    public ResilientSessionRepository(FindByIndexNameSessionRepository<S> delegate) {
        this.delegate = delegate;
    }

    @Override
    public S createSession() {
        return delegate.createSession();
    }

    @Override
    public void save(S session) {
        try {
            delegate.save(session);
        } catch (RuntimeException e) {
            if (!RedisGuard.isRedisFailure(e)) {
                throw e;
            }
            if (isLoginInProgress(session)) {
                log.warn("Redis 장애로 로그인 세션을 저장하지 못했습니다");
                throw new TemporarilyUnavailableException(e);
            }
            log.warn("Redis 장애로 익명 세션 저장을 건너뜁니다");
        }
    }

    @Override
    public S findById(String id) {
        try {
            return delegate.findById(id);
        } catch (SerializationException e) {
            log.warn("세션을 읽지 못해 비로그인으로 처리합니다: {}", e.getClass().getSimpleName());
            return null;
        } catch (RuntimeException e) {
            if (!RedisGuard.isRedisFailure(e)) {
                throw e;
            }
            log.warn("Redis 장애로 세션을 읽지 못해 비로그인으로 처리합니다");
            return null;
        }
    }

    @Override
    public void deleteById(String id) {
        delegate.deleteById(id);
    }

    @Override
    public Map<String, S> findByIndexNameAndIndexValue(String indexName, String indexValue) {
        return delegate.findByIndexNameAndIndexValue(indexName, indexValue);
    }

    private static boolean isLoginInProgress(Session session) {
        for (String name : CRITICAL_ATTRIBUTES) {
            Object value = session.getAttribute(name);
            if (value instanceof SecurityContext context) {
                Authentication auth = context.getAuthentication();
                if (auth != null
                        && auth.isAuthenticated()
                        && !(auth instanceof AnonymousAuthenticationToken)) {
                    return true;
                }
            } else if (value != null) {
                return true;
            }
        }
        return false;
    }
}
