package com.team.blog.discovery.application.trending;

import com.team.blog.discovery.infra.TrendingRepository;
import com.team.blog.discovery.infra.TrendingSnapshotStore;
import com.team.blog.discovery.infra.TrendingSnapshotStore.StoreUnavailableException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 트렌딩 스냅샷 작업 (012 T027, research R4, contracts §2, FR-009). {@code
 * blog.trending.refresh-cron}(10분마다)에 ShedLock {@code trendingSnapshot}(여러 서버 중 한 곳)으로 상위 {@code
 * snapshot-size}(100)개를 계산해 Redis에 {@code snapshot-ttl}(30분) 보관하고 {@code current}를 바꾼다. 0개여도 {@code
 * count = 0}을 쓴다(빈 순위와 만료를 구분, FR-015). 앱이 뜬 직후에도 같은 잠금으로 한 번 만든다({@code refresh-on-startup}).
 *
 * <p>Redis 장애면 WARN 후 끝 — 이전 스냅샷이 TTL까지 쓰이고, 그 뒤 읽기는 즉시 계산으로 대체한다(헌법 V). 트랜잭션 밖에서 Redis에 쓴다. 로그:
 * {@code trending snapshot id=… size=… took=…ms}.
 */
@Component
public class TrendingSnapshotJob {

    private static final Logger log = LoggerFactory.getLogger(TrendingSnapshotJob.class);

    public static final String LOCK_NAME = "trendingSnapshot";
    static final DateTimeFormatter SNAPSHOT_ID =
            DateTimeFormatter.ofPattern("yyyyMMddHHmm").withZone(ZoneOffset.UTC);

    private final TrendingRepository repository;
    private final TrendingSnapshotStore store;
    private final TrendingProperties properties;
    private final LockProvider lockProvider;
    private final Clock clock;

    public TrendingSnapshotJob(
            TrendingRepository repository,
            TrendingSnapshotStore store,
            TrendingProperties properties,
            LockProvider lockProvider,
            Clock clock) {
        this.repository = repository;
        this.store = store;
        this.properties = properties;
        this.lockProvider = lockProvider;
        this.clock = clock;
    }

    @Scheduled(cron = "${blog.trending.refresh-cron}")
    @SchedulerLock(name = LOCK_NAME, lockAtMostFor = "PT9M")
    public void run() {
        refresh(clock.instant());
    }

    /** 앱이 뜬 직후 한 번 (같은 잠금). 실패해도 기동을 막지 않는다. */
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (!properties.refreshOnStartup()) {
            return;
        }
        Instant now = clock.instant();
        try {
            new DefaultLockingTaskExecutor(lockProvider)
                    .executeWithLock(
                            (Runnable) () -> refresh(now),
                            new LockConfiguration(
                                    now, LOCK_NAME, Duration.ofMinutes(9), Duration.ZERO));
        } catch (RuntimeException e) {
            log.warn("기동 직후 트렌딩 스냅샷을 만들지 못했습니다: {}", e.getClass().getSimpleName());
        }
    }

    /**
     * 스냅샷 하나를 만든다.
     *
     * @return 만든 스냅샷 ID, Redis 장애면 {@code null}
     */
    public String refresh(Instant now) {
        long started = System.nanoTime();
        List<Long> ids = repository.compute(now, properties.snapshotSize());
        long computeMillis = (System.nanoTime() - started) / 1_000_000;
        String snapshotId = SNAPSHOT_ID.format(now);
        try {
            store.write(snapshotId, ids, properties.snapshotTtl());
        } catch (StoreUnavailableException e) {
            log.warn("Redis 장애로 트렌딩 스냅샷을 쓰지 못했습니다 (다음 주기에 다시)");
            return null;
        }
        log.info(
                "trending snapshot id={} size={} compute={}ms took={}ms",
                snapshotId,
                ids.size(),
                computeMillis,
                (System.nanoTime() - started) / 1_000_000);
        return snapshotId;
    }
}
