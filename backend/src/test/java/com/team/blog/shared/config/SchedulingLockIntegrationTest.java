package com.team.blog.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.support.IntegrationTestBase;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** ShedLock JDBC 잠금이 V2 shedlock 테이블을 쓰고, 같은 이름의 잠금은 한 번만 잡히는지 확인한다. */
class SchedulingLockIntegrationTest extends IntegrationTestBase {

    @Autowired LockProvider lockProvider;

    @Autowired ThreadPoolTaskScheduler taskScheduler;

    @Test
    void 같은_이름의_배치_잠금은_한_번만_잡힌다() {
        String name = "test-lock-" + System.nanoTime();
        LockConfiguration config =
                new LockConfiguration(Instant.now(), name, Duration.ofMinutes(1), Duration.ZERO);
        Optional<SimpleLock> first = lockProvider.lock(config);
        Optional<SimpleLock> second = lockProvider.lock(config);
        assertThat(first).isPresent();
        assertThat(second).isEmpty();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM shedlock WHERE name = ?", Long.class, name))
                .isEqualTo(1);
        first.get().unlock();
        assertThat(lockProvider.lock(config)).isPresent();
    }

    @Test
    void 스케줄러_스레드_수는_설정값을_따른다() {
        assertThat(taskScheduler.getScheduledThreadPoolExecutor().getCorePoolSize()).isEqualTo(2);
    }
}
