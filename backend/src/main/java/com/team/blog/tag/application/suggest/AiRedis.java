package com.team.blog.tag.application.suggest;

import com.team.blog.post.application.exception.AutosaveUnavailableException;
import com.team.blog.shared.infra.redis.RedisGuard;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * 이 기능의 Redis 호출 (013 research R11, data-model §2). 모두 002 {@link RedisGuard}를 거치고 트랜잭션 밖에서 부른다.
 *
 * <ul>
 *   <li>{@link #call}: 장애(연결 실패·시간 초과·열린 회로)와 메모리 부족({@link AutosaveUnavailableException} — 002 문구라
 *       이 기능에 맞지 않음)을 503 {@code AI_UNAVAILABLE} {@code STORE_UNAVAILABLE}로 바꾼다.
 *   <li>{@link #quietly}: 되돌리기·정리처럼 실패해도 응답을 바꾸지 않을 호출. 장애면 그냥 넘어간다(키에는 모두 TTL이 있다).
 * </ul>
 */
@Component
public class AiRedis {

    private final RedisGuard guard;

    public AiRedis(RedisGuard guard) {
        this.guard = guard;
    }

    public <T> T call(Supplier<T> action) {
        try {
            return guard.callWrite(
                    action,
                    () -> {
                        throw new AiUnavailableException(AiUnavailableReason.STORE_UNAVAILABLE);
                    });
        } catch (AutosaveUnavailableException e) {
            throw new AiUnavailableException(AiUnavailableReason.STORE_UNAVAILABLE);
        }
    }

    public void quietly(Runnable action) {
        try {
            guard.runWrite(action, () -> {});
        } catch (AutosaveUnavailableException e) {
            // 메모리 부족 — 되돌리기를 못 해도 TTL이 정리한다
        }
    }
}
