package com.team.blog.media.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.media.application.ImageCleanupJob;
import com.team.blog.media.support.ImageFixtures;
import com.team.blog.support.IntegrationTestBase;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 정리 배치 잠금 (003 T079 US7, ShedLock {@code imageCleanup}). 예약 실행 경로({@link ImageCleanupJob#run()})는
 * 같은 {@link LockProvider}(DB {@code shedlock})를 쓰는 다른 인스턴스가 잠금을 쥐고 있으면 아무 것도 하지 않는다.
 */
class ImageCleanupLockIT extends IntegrationTestBase {

    @Autowired ImageCleanupJob job;
    @Autowired LockProvider lockProvider;

    private long oldImage(long me) {
        return new ImageFixtures(jdbc)
                .image(me)
                .createdAt(Instant.now().minus(Duration.ofDays(2)))
                .create();
    }

    private long images() {
        return jdbc.queryForObject("SELECT count(*) FROM image", Long.class);
    }

    @Test
    void 다른_인스턴스가_imageCleanup_잠금을_쥐고_있으면_건너뛴다() {
        long me = members().member().create();
        oldImage(me);
        Optional<SimpleLock> other =
                lockProvider.lock(
                        new LockConfiguration(
                                Instant.now(),
                                "imageCleanup",
                                Duration.ofMinutes(5),
                                Duration.ZERO));
        assertThat(other).isPresent();
        try {
            job.run();
            assertThat(images()).isOne();
        } finally {
            other.get().unlock();
        }

        job.run();

        assertThat(images()).isZero();
    }

    @Test
    void 두_인스턴스가_동시에_실행해도_한_번만_처리한다() throws Exception {
        long me = members().member().create();
        for (int i = 0; i < 50; i++) {
            oldImage(me);
        }
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Void>> runs = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                runs.add(
                        () -> {
                            job.run();
                            return null;
                        });
            }
            for (Future<Void> f : pool.invokeAll(runs)) {
                f.get();
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(images()).isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM shedlock WHERE name = 'imageCleanup'",
                                Long.class))
                .isOne();
    }
}
