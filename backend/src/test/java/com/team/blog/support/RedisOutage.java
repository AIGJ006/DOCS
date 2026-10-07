package com.team.blog.support;

import com.github.dockerjava.api.DockerClient;
import java.time.Duration;
import java.time.Instant;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.GenericContainer;

/**
 * Redis 장애 재현: Testcontainers Redis 컨테이너를 일시 정지(docker pause)했다가 close()에서 되살린다.
 *
 * <pre>{@code
 * try (RedisOutage outage = RedisOutage.start()) {
 *     // Redis 응답 없음
 * }
 * // Redis 복구
 * }</pre>
 */
public final class RedisOutage implements AutoCloseable {

    private static volatile boolean paused;

    private RedisOutage() {}

    public static RedisOutage start() {
        GenericContainer<?> redis = IntegrationTestBase.redisContainer();
        DockerClient client = redis.getDockerClient();
        client.pauseContainerCmd(redis.getContainerId()).exec();
        paused = true;
        return new RedisOutage();
    }

    @Override
    public void close() {
        resume();
    }

    /** 앞선 테스트가 정지한 채로 끝났어도 다음 테스트 전에 되살린다. */
    static void ensureRunning() {
        if (paused) {
            resume();
        }
    }

    private static synchronized void resume() {
        if (!paused) {
            return;
        }
        GenericContainer<?> redis = IntegrationTestBase.redisContainer();
        redis.getDockerClient().unpauseContainerCmd(redis.getContainerId()).exec();
        paused = false;
        waitUntilPong(redis);
    }

    private static void waitUntilPong(GenericContainer<?> redis) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (Instant.now().isBefore(deadline)) {
            try {
                ExecResult result = redis.execInContainer("redis-cli", "ping");
                if (result.getStdout().contains("PONG")) {
                    return;
                }
            } catch (Exception ignored) {
                // 재시도
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        throw new IllegalStateException("Redis가 복구되지 않았습니다");
    }
}
