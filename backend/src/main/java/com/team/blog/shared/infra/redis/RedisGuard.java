package com.team.blog.shared.infra.redis;

import com.team.blog.post.application.exception.AutosaveUnavailableException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Redis 장애 판정과 대체 경로 (02 §2-1: 글 읽기는 계속, 보안상 필요한 것만 거부).
 *
 * <p>{@link #call(Supplier, Supplier)}는 Redis 연결 실패·응답 시간 초과({@code
 * spring.data.redis.timeout})·Redis 오류를 잡아 대체 값을 돌려준다. 대체 값에서 예외를 던지면 거부(예: 토큰 확인 → 503)가 된다.
 *
 * <p><b>회로 차단기 (002 research B-5)</b>: 모든 호출을 Resilience4j {@code CircuitBreaker("redis")}로 감싼다(설정
 * {@code resilience4j.circuitbreaker.instances.redis}: 최근 20회 중 50% 실패 → 30초 열림 → 반열림 5회). 열려 있으면
 * Redis를 부르지 않고 바로 대체 경로를 쓴다(장애 동안 매 요청 시간 초과를 기다리지 않음). 실패로 세는 것은 연결 실패·시간 초과뿐이다. 그 밖의 Redis 오류는
 * 대체 경로를 쓰되 세지 않는다.
 *
 * <p><b>메모리 부족({@code OOM})</b>: 장애가 아니라 쓰기 거부다. 세지 않고 대체 경로도 쓰지 않으며 {@link
 * AutosaveUnavailableException}(503)을 던진다 — 자동 저장을 DB로 우회하지 않고 브라우저가 재시도한다(FR-018, 밀어내지 않음). 002
 * 문서의 {@code execute(call, fallback)}·{@code isOpen()}은 {@link #call}·{@code !}{@link
 * #isAvailable()}이다.
 *
 * <p><b>트랜잭션 경계 (002 T119, 05 J-5, research A-6)</b>: DB 커밋과 함께 되돌릴 수 없는 Redis 쓰기는 트랜잭션 밖(커밋 후)에서만
 * 한다. 쓰기는 {@link #callWrite}·{@link #runWrite}로 부르고, 실제 트랜잭션 안에서 부르면 {@code
 * blog.redis.fail-on-write-in-transaction=true}(test 프로필)일 때 {@link IllegalStateException}, 아니면(운영
 * 기본) 경고 로그를 남긴다. 읽기({@link #call}·{@link #run})와 {@link #runAfterCommit}으로 커밋 뒤에 돌리는 쓰기(006 {@code
 * flushNow} 경로의 보관분 정리)는 허용 목록이다.
 */
@Component
public class RedisGuard {

    private static final Logger log = LoggerFactory.getLogger(RedisGuard.class);

    /** 회로 차단기 이름 ({@code resilience4j.circuitbreaker.instances.redis}). */
    public static final String CIRCUIT_BREAKER = "redis";

    /** {@link #runAfterCommit}이 커밋 뒤 작업을 돌리는 중인가 (그 사이에도 트랜잭션 동기화 상태는 남아 있다). */
    private static final ThreadLocal<Boolean> AFTER_COMMIT = new ThreadLocal<>();

    private final StringRedisTemplate redis;
    private final CircuitBreaker breaker;
    private final boolean failOnWriteInTransaction;

    public RedisGuard(
            StringRedisTemplate redis,
            CircuitBreakerRegistry circuitBreakers,
            @Value("${blog.redis.fail-on-write-in-transaction:false}")
                    boolean failOnWriteInTransaction) {
        this.redis = redis;
        this.breaker = circuitBreakers.circuitBreaker(CIRCUIT_BREAKER);
        this.failOnWriteInTransaction = failOnWriteInTransaction;
    }

    /** Redis 쓰기. {@link #call}과 같고, 트랜잭션 안이면 막거나(test) 경고한다(운영). */
    public <T> T callWrite(Supplier<T> action, Supplier<T> fallback) {
        requireOutsideTransaction();
        return call(action, fallback);
    }

    /** 결과가 없는 Redis 쓰기. {@link #run}과 같고, 트랜잭션 안이면 막거나(test) 경고한다(운영). */
    public void runWrite(Runnable action, Runnable fallback) {
        requireOutsideTransaction();
        run(action, fallback);
    }

    /**
     * 트랜잭션이 있으면 커밋 뒤에, 없으면 바로 {@code action}을 돌린다. 커밋 뒤 작업 안의 Redis 쓰기는 트랜잭션 경계 검사를 통과한다. 롤백이면 돌리지
     * 않는다. 예외는 경고 로그 후 삼킨다(커밋은 이미 끝났다).
     */
    public static void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        AFTER_COMMIT.set(Boolean.TRUE);
                        try {
                            action.run();
                        } catch (RuntimeException e) {
                            log.warn("커밋 후 Redis 작업 실패: {}", e.getClass().getSimpleName());
                        } finally {
                            AFTER_COMMIT.remove();
                        }
                    }
                });
    }

    private void requireOutsideTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || Boolean.TRUE.equals(AFTER_COMMIT.get())) {
            return;
        }
        if (failOnWriteInTransaction) {
            throw new IllegalStateException("트랜잭션 안에서 Redis에 쓰려고 했습니다 (05 J-5: 커밋 후에 쓰세요)");
        }
        log.warn(
                "트랜잭션 안에서 Redis에 씁니다 (05 J-5 위반, 커밋 후로 옮겨야 합니다)",
                new IllegalStateException("Redis write in transaction"));
    }

    /**
     * Redis 호출. 회로가 열려 있거나 장애면 경고 로그 후 {@code fallback} 결과.
     *
     * @throws AutosaveUnavailableException Redis 메모리 부족({@code OOM})
     */
    public <T> T call(Supplier<T> action, Supplier<T> fallback) {
        if (!breaker.tryAcquirePermission()) {
            log.warn("Redis 회로가 열려 있어 대체 경로를 씁니다");
            return fallback.get();
        }
        long start = System.nanoTime();
        try {
            T result = action.get();
            breaker.onSuccess(System.nanoTime() - start, TimeUnit.NANOSECONDS);
            return result;
        } catch (RuntimeException e) {
            if (isOutOfMemory(e)) {
                breaker.releasePermission();
                log.warn("Redis 메모리 부족으로 쓰기를 받지 못했습니다");
                throw new AutosaveUnavailableException(e);
            }
            if (!isRedisFailure(e)) {
                breaker.releasePermission();
                throw e;
            }
            if (isConnectionFailureOrTimeout(e)) {
                breaker.onError(System.nanoTime() - start, TimeUnit.NANOSECONDS, e);
            } else {
                breaker.releasePermission();
            }
            log.warn("Redis 장애로 대체 경로를 씁니다: {}", e.getClass().getSimpleName());
            return fallback.get();
        }
    }

    /** 결과가 없는 Redis 호출. 장애면 경고 로그 후 {@code fallback} 실행. */
    public void run(Runnable action, Runnable fallback) {
        call(
                () -> {
                    action.run();
                    return null;
                },
                () -> {
                    fallback.run();
                    return null;
                });
    }

    /** 지금 Redis가 응답하는가: 회로가 열려 있지 않고 PING이 PONG. */
    public boolean isAvailable() {
        CircuitBreaker.State state = breaker.getState();
        if (state == CircuitBreaker.State.OPEN || state == CircuitBreaker.State.FORCED_OPEN) {
            return false;
        }
        try {
            String pong = redis.execute((RedisCallback<String>) connection -> connection.ping());
            return "PONG".equalsIgnoreCase(pong);
        } catch (RuntimeException e) {
            if (isRedisFailure(e)) {
                return false;
            }
            throw e;
        }
    }

    /** 회로 차단기에 실패로 세는 예외인가 (연결 실패·시간 초과만). */
    static boolean isConnectionFailureOrTimeout(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof RedisConnectionFailureException
                    || t instanceof QueryTimeoutException) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }

    /** Redis가 {@code maxmemory}를 넘어 쓰기를 거부했는가 ({@code OOM command not allowed …}). */
    public static boolean isOutOfMemory(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String message = t.getMessage();
            if (message != null && message.strip().toUpperCase(Locale.ROOT).startsWith("OOM ")) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }

    /** Redis 장애로 볼 예외인가 (연결 실패·시간 초과·Redis 시스템 오류). */
    public static boolean isRedisFailure(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof RedisConnectionFailureException
                    || t instanceof QueryTimeoutException
                    || t instanceof RedisSystemException) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }
}
