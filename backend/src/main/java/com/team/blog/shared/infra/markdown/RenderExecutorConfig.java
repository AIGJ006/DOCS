package com.team.blog.shared.infra.markdown;

import com.team.blog.shared.application.markdown.MarkdownProperties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 렌더링 전용 고정 크기 풀 (research B-7). 느린 렌더링이 요청 스레드를 붙잡지 않게 {@link DefaultContentRenderer}가 이 풀에서 렌더링하고
 * 제한 시간까지만 기다린다. 크기는 {@code blog.markdown.render-threads}(4).
 */
@Configuration(proxyBeanMethods = false)
public class RenderExecutorConfig {

    public static final String RENDER_EXECUTOR = "renderExecutor";

    @Bean(name = RENDER_EXECUTOR, destroyMethod = "shutdownNow")
    public ExecutorService renderExecutor(MarkdownProperties properties) {
        int threads = properties.renderThreads();
        return new ThreadPoolExecutor(
                threads,
                threads,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                namedDaemonThreads("blog-render-"));
    }

    private static ThreadFactory namedDaemonThreads(String prefix) {
        AtomicInteger seq = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + seq.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
