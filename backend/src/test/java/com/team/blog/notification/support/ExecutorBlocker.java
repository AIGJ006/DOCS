package com.team.blog.notification.support;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 이벤트 실행기를 잠시 막는 테스트 도구 (011 T016 — "이벤트를 발행한 뒤 처리 전에 상태가 바뀜" 순서를 만든다). 기본 스레드 수만큼 기다리는 작업을 넣어 그 뒤
 * 작업이 대기열에서 기다리게 한다(대기열이 가득 차기 전에는 스레드가 늘지 않는다).
 */
public final class ExecutorBlocker implements AutoCloseable {

    private final CountDownLatch release = new CountDownLatch(1);

    private ExecutorBlocker() {}

    public static ExecutorBlocker block(ThreadPoolTaskExecutor executor)
            throws InterruptedException {
        ExecutorBlocker blocker = new ExecutorBlocker();
        int threads = executor.getCorePoolSize();
        CountDownLatch started = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            executor.execute(
                    () -> {
                        started.countDown();
                        try {
                            blocker.release.await(10, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    });
        }
        if (!started.await(5, TimeUnit.SECONDS)) {
            blocker.close();
            throw new IllegalStateException("실행기를 막지 못했습니다");
        }
        return blocker;
    }

    @Override
    public void close() {
        release.countDown();
    }
}
