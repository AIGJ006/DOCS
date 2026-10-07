package com.team.blog.shared.config;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 스케줄러와 배치 잠금(ShedLock JDBC, {@code shedlock} 테이블 — V2).
 *
 * <p><b>배치 작성 규칙</b>
 *
 * <ul>
 *   <li>모든 {@code @Scheduled} 배치는 {@code @SchedulerLock(name = "...")}을 붙여 서버가 여러 대여도 한 번만 실행되게 한다.
 *       잠금 시각은 DB 시각을 쓴다({@code usingDbTime()}). 기본 최대 잠금은 10분이며 배치마다 {@code lockAtMostFor}로 조정한다.
 *   <li>cron 배치는 서비스 시간대를 쓰도록 {@code zone = "${blog.time-zone}"}을 지정한다. 예: 정리 배치 03:30 KST
 *       {@code @Scheduled(cron = "0 30 3 * * *", zone = "${blog.time-zone}")} (README "배치 잠금").
 *   <li>스레드 수는 {@code blog.scheduling.pool-size}(기본 2).
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class SchedulingConfig {

    @Bean
    LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .withTableName("shedlock")
                        .usingDbTime()
                        .build());
    }

    @Bean
    ThreadPoolTaskScheduler taskScheduler(CoreProperties properties) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(properties.scheduling().poolSize());
        scheduler.setThreadNamePrefix("blog-scheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        return scheduler;
    }
}
