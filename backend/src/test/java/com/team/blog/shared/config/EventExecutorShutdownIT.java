package com.team.blog.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.support.IntegrationTestBase;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 이벤트 실행기 대기열·종료 대기 (011 T005, FR-003, research R5). 대기열이 가득 차면 버리고 WARN, 종료 때 {@code
 * await-termination}만큼 기다리고 그 안에 못 끝낸 작업 수를 WARN으로 남긴다.
 */
@ExtendWith(OutputCaptureExtension.class)
class EventExecutorShutdownIT extends IntegrationTestBase {

    @Autowired
    @Qualifier("eventExecutor")
    private ThreadPoolTaskExecutor eventExecutor;

    @Autowired private CoreProperties properties;

    @Test
    void 설정값은_대기열_1000_종료_대기_20초() {
        assertThat(eventExecutor.getQueueCapacity()).isEqualTo(1000);
        assertThat(properties.async().event().queueCapacity()).isEqualTo(1000);
        assertThat(properties.async().event().awaitTermination()).isEqualTo(Duration.ofSeconds(20));
        // 메일 실행기는 기본 10초 그대로
        assertThat(properties.async().mail().awaitTermination()).isEqualTo(Duration.ofSeconds(10));
        assertThat(eventExecutor).isInstanceOf(AsyncConfig.ReportingTaskExecutor.class);
    }

    @Test
    void 대기열이_가득_차면_버리고_경고한다(CapturedOutput output) throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        ThreadPoolTaskExecutor executor =
                AsyncConfig.executor(
                        "test-event-", new CoreProperties.Pool(1, 1, 1, Duration.ofSeconds(1)));
        executor.initialize();
        AtomicInteger ran = new AtomicInteger();
        try {
            executor.execute(() -> await(release)); // 실행 중 1
            executor.execute(ran::incrementAndGet); // 대기열 1
            executor.execute(ran::incrementAndGet); // 버림
            assertThat(output.getOut()).contains("대기열이 가득 차 작업을 버렸습니다").contains("test-event-");
        } finally {
            release.countDown();
            executor.shutdown();
        }
        assertThat(ran.get()).isEqualTo(1);
    }

    @Test
    void 종료_대기_안에_못_끝낸_작업_수를_경고한다(CapturedOutput output) {
        CountDownLatch release = new CountDownLatch(1);
        ThreadPoolTaskExecutor executor =
                AsyncConfig.executor(
                        "test-stop-", new CoreProperties.Pool(1, 1, 10, Duration.ofSeconds(1)));
        executor.initialize();
        try {
            executor.execute(() -> await(release));
            executor.execute(() -> {});
            executor.execute(() -> {});
            long started = System.nanoTime();
            executor.shutdown();
            long waitedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            assertThat(waitedMillis).isBetween(900L, 5_000L);
            assertThat(output.getOut())
                    .contains("비동기 실행기 종료 대기 시간 안에 끝내지 못한 작업")
                    .contains("executor=test-stop-")
                    .contains("remaining=3");
        } finally {
            release.countDown();
        }
    }

    @Test
    void 시간_안에_끝나면_경고하지_않는다(CapturedOutput output) {
        ThreadPoolTaskExecutor executor =
                AsyncConfig.executor(
                        "test-quick-", new CoreProperties.Pool(1, 1, 10, Duration.ofSeconds(1)));
        executor.initialize();
        executor.execute(() -> {});
        executor.shutdown();
        assertThat(output.getOut()).doesNotContain("executor=test-quick-");
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
