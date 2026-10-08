package com.team.blog.post.support;

import com.team.blog.post.application.spi.PostPurgeStep;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 006 완전 삭제 확장점 시험용 단계 (T005·T054·T072). 테스트 소스의 {@code @Component}라 모든 통합 테스트 컨텍스트에 함께 등록된다 — 새
 * 컨텍스트(연결 풀)를 만들지 않으려고 {@code @TestConfiguration} 대신 이렇게 둔다. {@link #arm()} 하기 전에는 아무것도 하지 않는다.
 *
 * <p>실제 단계(신고 10·사진 20)와 순서가 겹치지 않게 앞 단계는 5, 뒤 단계는 25다. 호출 시점에 {@code post} 행이 있었는지도 기록한다(같은 트랜잭션의
 * 연결로 조회).
 */
@Profile("test")
@Component
public class PurgeStepProbe {

    /** 단계 호출 한 건. */
    public record Call(int order, long postId, boolean postRowPresent) {}

    private final JdbcTemplate jdbc;
    private final List<Call> calls = new CopyOnWriteArrayList<>();
    private final Set<Long> failing = ConcurrentHashMap.newKeySet();
    private volatile boolean armed;
    private volatile CountDownLatch entered;
    private volatile CountDownLatch release;

    public PurgeStepProbe(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 기록을 시작한다(앞선 기록·실패·멈춤 설정은 지운다). */
    public void arm() {
        reset();
        armed = true;
    }

    /** 기록을 멈추고 모두 지운다. */
    public void reset() {
        armed = false;
        calls.clear();
        failing.clear();
        CountDownLatch r = release;
        if (r != null) {
            r.countDown();
        }
        entered = null;
        release = null;
    }

    /** 이 글의 뒤 단계(25)가 예외를 던지게 한다. */
    public void failOn(long postId) {
        failing.add(postId);
    }

    /** 다음 앞 단계(5) 호출이 {@code release}가 열릴 때까지 기다리게 한다(한 번만). 들어오면 {@code entered}를 연다. */
    public void blockNext(CountDownLatch entered, CountDownLatch release) {
        this.entered = entered;
        this.release = release;
    }

    public List<Call> calls() {
        return List.copyOf(calls);
    }

    /** 그 글에 대해 불린 단계 순서. */
    public List<Integer> ordersFor(long postId) {
        return calls.stream().filter(c -> c.postId() == postId).map(Call::order).toList();
    }

    void record(int order, long postId) {
        if (!armed) {
            return;
        }
        boolean present =
                Boolean.TRUE.equals(
                        jdbc.queryForObject(
                                "SELECT EXISTS (SELECT 1 FROM post WHERE id = ?)",
                                Boolean.class,
                                postId));
        calls.add(new Call(order, postId, present));
        if (order == Early.ORDER) {
            CountDownLatch in = entered;
            CountDownLatch out = release;
            if (in != null && out != null) {
                entered = null;
                in.countDown();
                try {
                    out.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        if (order == Late.ORDER && failing.contains(postId)) {
            throw new IllegalStateException("probe failure for post " + postId);
        }
    }

    /** 앞 단계 (order 5). */
    @Profile("test")
    @Component
    public static class Early implements PostPurgeStep {
        static final int ORDER = 5;
        private final PurgeStepProbe probe;

        public Early(PurgeStepProbe probe) {
            this.probe = probe;
        }

        @Override
        public int order() {
            return ORDER;
        }

        @Override
        public void beforePurge(long postId) {
            probe.record(ORDER, postId);
        }
    }

    /** 뒤 단계 (order 25). */
    @Profile("test")
    @Component
    public static class Late implements PostPurgeStep {
        static final int ORDER = 25;
        private final PurgeStepProbe probe;

        public Late(PurgeStepProbe probe) {
            this.probe = probe;
        }

        @Override
        public int order() {
            return ORDER;
        }

        @Override
        public void beforePurge(long postId) {
            probe.record(ORDER, postId);
        }
    }
}
