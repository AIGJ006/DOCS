package com.team.blog.account.application;

import com.team.blog.shared.error.TemporarilyUnavailableException;
import com.team.blog.shared.infra.redis.RedisGuard;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

/**
 * 회원의 모든 세션을 지운다 (R-03, FR-044·045, 42 P-7). 비밀번호 재설정·변경(US4), 정지(014), 탈퇴(015)가 호출한다. 004 plan의
 * {@code MemberSessionService}는 이 클래스를 가리킨다.
 *
 * <p>세션 저장소의 principal 이름 = 회원 번호 문자열({@code MemberPrincipal#getName()}). Redis 장애면 지웠다고 볼 수 없으므로
 * 503 {@link TemporarilyUnavailableException}을 던진다(호출한 트랜잭션도 되돌려야 한다).
 */
@Component
public class SessionTerminator {

    private static final Logger log = LoggerFactory.getLogger(SessionTerminator.class);

    private final FindByIndexNameSessionRepository<? extends Session> sessions;

    public SessionTerminator(FindByIndexNameSessionRepository<? extends Session> sessions) {
        this.sessions = sessions;
    }

    /**
     * @param exceptSessionId 남길 세션(비밀번호 변경 때 지금 기기) — 비어 있으면 모두 지운다
     * @return 지운 세션 수
     */
    public int terminateAll(long memberId, Optional<String> exceptSessionId) {
        try {
            Set<String> ids = sessions.findByPrincipalName(String.valueOf(memberId)).keySet();
            int deleted = 0;
            for (String id : ids) {
                if (exceptSessionId.isPresent() && exceptSessionId.get().equals(id)) {
                    continue;
                }
                sessions.deleteById(id);
                deleted++;
            }
            log.info("회원 세션 삭제 memberId={} count={}", memberId, deleted);
            return deleted;
        } catch (RuntimeException e) {
            if (RedisGuard.isRedisFailure(e)) {
                throw new TemporarilyUnavailableException(e);
            }
            throw e;
        }
    }
}
