package com.team.blog.interaction.application;

import com.team.blog.interaction.infra.ViewDailyRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 일별 조회수 보관 기간 정리 (009 T038, US4 #3, FR-032, SC-011). 매일 {@code blog.view.retention-cron}에 서비스 시간대
 * 오늘에서 {@code blog.view.daily-retention}(기본 90일)을 뺀 날짜보다 이른 행을 지운다 — 경계 날짜는 남긴다. 누적 {@code
 * post.view_count}는 건드리지 않는다. ShedLock {@code view-daily-retention}.
 */
@Component
public class ViewDailyRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(ViewDailyRetentionJob.class);

    private final ViewDailyRepository daily;
    private final ViewProperties properties;
    private final Clock clock;
    private final ZoneId zone;

    public ViewDailyRetentionJob(
            ViewDailyRepository daily,
            ViewProperties properties,
            Clock clock,
            ZoneId serviceZoneId) {
        this.daily = daily;
        this.properties = properties;
        this.clock = clock;
        this.zone = serviceZoneId;
    }

    @Scheduled(cron = "${blog.view.retention-cron}", zone = "${blog.time-zone}")
    @SchedulerLock(name = "view-daily-retention", lockAtMostFor = "PT30M")
    public void run() {
        int deleted = purge(LocalDate.now(clock.withZone(zone)));
        if (deleted > 0) {
            log.info("일별 조회수 보관 기간 정리: {}행 삭제", deleted);
        }
    }

    /**
     * @param today 서비스 시간대 오늘
     * @return 지운 행 수
     */
    @Transactional
    public int purge(LocalDate today) {
        return daily.deleteOlderThan(today.minusDays(properties.dailyRetentionDays()));
    }
}
