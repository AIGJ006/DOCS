package com.team.blog.post.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * 글 작성 운영 지표 (002 T120, plan "운영"). 값만 센다 — 경고 로그는 각 자리에서 postId·memberId만 남기고 제목·본문은 남기지 않는다.
 *
 * <ul>
 *   <li>{@code blog.post.autosave.db-fallback} — Redis 장애로 자동 저장을 DB에 바로 쓴 횟수
 *   <li>{@code blog.post.publish.idempotency-skipped} — Redis 장애로 발행 멱등 키 처리를 건너뛴 횟수
 *   <li>{@code blog.post.autosave.release-failed} — 발행·변경 취소 뒤 ⑨ Redis 보관분 정리를 못 한 횟수
 *   <li>{@code blog.post.autosave.flush-failed} — 마지막 1분 반영 회차에서 반영하지 못한 글 수 (게이지)
 *   <li>{@code blog.post.rerender.remaining} — 마지막 다시 렌더링 회차 뒤 남은 발행 글 수 (게이지)
 * </ul>
 */
@Component
public class PostAuthoringMetrics {

    private final Counter autosaveDbFallback;
    private final Counter idempotencySkipped;
    private final Counter releaseFailed;
    private final AtomicLong flushFailed = new AtomicLong();
    private final AtomicLong rerenderRemaining = new AtomicLong();

    public PostAuthoringMetrics(MeterRegistry registry) {
        this.autosaveDbFallback =
                Counter.builder("blog.post.autosave.db-fallback")
                        .description("Redis 장애로 자동 저장을 DB에 바로 쓴 횟수")
                        .register(registry);
        this.idempotencySkipped =
                Counter.builder("blog.post.publish.idempotency-skipped")
                        .description("Redis 장애로 발행 멱등 키 처리를 건너뛴 횟수")
                        .register(registry);
        this.releaseFailed =
                Counter.builder("blog.post.autosave.release-failed")
                        .description("발행·변경 취소 뒤 Redis 보관분 정리를 못 한 횟수")
                        .register(registry);
        registry.gauge("blog.post.autosave.flush-failed", flushFailed);
        registry.gauge("blog.post.rerender.remaining", rerenderRemaining);
    }

    public void autosaveDbFallback() {
        autosaveDbFallback.increment();
    }

    public void idempotencySkipped() {
        idempotencySkipped.increment();
    }

    public void releaseFailed() {
        releaseFailed.increment();
    }

    public void flushFailed(long posts) {
        flushFailed.set(posts);
    }

    public void rerenderRemaining(long posts) {
        rerenderRemaining.set(posts);
    }
}
