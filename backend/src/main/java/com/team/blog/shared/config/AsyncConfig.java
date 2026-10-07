package com.team.blog.shared.config;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 비동기 실행기. 도메인 이벤트 리스너는 {@code @Async("eventExecutor")}, 메일 발송은 {@code @Async("mailExecutor")}를 쓴다.
 * 크기는 {@code blog.async.event.*}·{@code blog.async.mail.*}. 대기열이 가득 차면 경고 로그를 남기고 작업을 버린다(부가 기능의
 * 실패가 핵심을 막지 않음 — constitution V). 이름 없는 {@code @Async}는 {@code eventExecutor}를 쓴다.
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    private final CoreProperties properties;

    public AsyncConfig(CoreProperties properties) {
        this.properties = properties;
    }

    @Bean
    public ThreadPoolTaskExecutor eventExecutor() {
        return executor("blog-event-", properties.async().event());
    }

    @Bean
    public ThreadPoolTaskExecutor mailExecutor() {
        return executor("blog-mail-", properties.async().mail());
    }

    @Override
    public Executor getAsyncExecutor() {
        return eventExecutor();
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) ->
                log.warn(
                        "비동기 작업 실패: {}.{}",
                        method.getDeclaringClass().getSimpleName(),
                        method.getName(),
                        ex);
    }

    private static ThreadPoolTaskExecutor executor(String prefix, CoreProperties.Pool pool) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix(prefix);
        executor.setCorePoolSize(pool.coreSize());
        executor.setMaxPoolSize(Math.max(pool.coreSize(), pool.maxSize()));
        executor.setQueueCapacity(pool.queueCapacity());
        executor.setRejectedExecutionHandler(discardWithWarning(prefix));
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }

    private static RejectedExecutionHandler discardWithWarning(String prefix) {
        return (task, pool) ->
                log.warn(
                        "비동기 실행기 대기열이 가득 차 작업을 버렸습니다: executor={}, queue={}",
                        prefix,
                        pool.getQueue().size());
    }
}
