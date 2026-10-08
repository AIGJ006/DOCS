package com.team.blog.notification.support;

import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 비동기 알림 리스너 기다리기 (011 T014). 고정 sleep을 쓰지 않는다(Awaitility, 최대 5초).
 *
 * <p>"생기지 않음"은 {@link #idle()}로 확인한다: 커밋 뒤 리스너 작업은 커밋한 스레드에서 실행기 대기열에 들어가므로, 요청이 끝난 뒤 실행기가 비면 그 이벤트
 * 처리도 끝난 것이다.
 */
public final class NotificationAwait {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final JdbcTemplate jdbc;
    private final ThreadPoolTaskExecutor executor;

    public NotificationAwait(JdbcTemplate jdbc, ThreadPoolTaskExecutor executor) {
        this.jdbc = jdbc;
        this.executor = executor;
    }

    /** 받는 사람의 알림 수가 {@code n}이 될 때까지. */
    public void untilCount(long receiverId, long n) {
        await().atMost(TIMEOUT)
                .pollInterval(Duration.ofMillis(20))
                .until(
                        () ->
                                jdbc.queryForObject(
                                                "SELECT count(*) FROM notification WHERE receiver_id = ?",
                                                Long.class,
                                                receiverId)
                                        == n);
        idle();
    }

    /** 이벤트 실행기의 대기열이 비고 실행 중 작업이 없을 때까지. */
    public void idle() {
        ThreadPoolExecutor pool = executor.getThreadPoolExecutor();
        await().atMost(TIMEOUT)
                .pollInterval(Duration.ofMillis(10))
                .until(() -> pool.getQueue().isEmpty() && pool.getActiveCount() == 0);
    }
}
