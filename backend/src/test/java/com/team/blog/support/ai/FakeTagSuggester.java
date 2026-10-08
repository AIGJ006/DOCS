package com.team.blog.support.ai;

import com.team.blog.tag.application.suggest.Provider;
import com.team.blog.tag.application.suggest.SuggestOutcome;
import com.team.blog.tag.application.suggest.TagSuggestInput;
import com.team.blog.tag.application.suggest.TagSuggester;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.core.Ordered;

/**
 * 시험용 가짜 공급자 (013 T015). {@link FakeAiConfiguration}이 공급자마다 하나씩 등록하고, 순서가 가장 앞서 라우터가 실제 Bean 대신
 * 고른다.
 *
 * <ul>
 *   <li>{@link #respond}로 다음 응답들을 순서대로 정하고, 다 쓰면 기본 응답(성공 5개)
 *   <li>받은 입력·호출 수를 기록한다
 *   <li>{@link #hold()}로 다음 호출을 {@link #release()}까지 멈춘다(동시 처리 시험), {@link #delay}로 지연을 흉내 낸다
 *   <li>{@link #configured(boolean)}로 "키 없음"·"자체 AI 꺼짐"을 흉내 낸다
 * </ul>
 */
public class FakeTagSuggester implements TagSuggester, Ordered {

    public static final List<String> DEFAULT_TAGS =
            List.of("spring", "jpa", "hibernate", "java", "database");

    private final Provider provider;
    private final Deque<SuggestOutcome> scripted = new ArrayDeque<>();
    private final List<TagSuggestInput> inputs = new CopyOnWriteArrayList<>();
    private final AtomicInteger calls = new AtomicInteger();
    private volatile boolean configured = true;
    private volatile Duration delay = Duration.ZERO;
    private volatile CountDownLatch entered;
    private volatile CountDownLatch gate;

    public FakeTagSuggester(Provider provider) {
        this.provider = provider;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Provider provider() {
        return provider;
    }

    @Override
    public boolean isConfigured() {
        return configured;
    }

    @Override
    public SuggestOutcome suggest(TagSuggestInput input) {
        calls.incrementAndGet();
        inputs.add(input);
        CountDownLatch e = entered;
        CountDownLatch g = gate;
        if (e != null) {
            e.countDown();
        }
        try {
            if (g != null) {
                g.await(10, TimeUnit.SECONDS);
            }
            if (!delay.isZero()) {
                Thread.sleep(delay.toMillis());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        synchronized (scripted) {
            SuggestOutcome next = scripted.poll();
            return next != null ? next : new SuggestOutcome.Success(DEFAULT_TAGS);
        }
    }

    /** 앞으로의 응답 (순서대로, 다 쓰면 기본 응답). */
    public FakeTagSuggester respond(SuggestOutcome... outcomes) {
        synchronized (scripted) {
            scripted.addAll(List.of(outcomes));
        }
        return this;
    }

    /** 성공 응답 하나. */
    public FakeTagSuggester respondTags(String... tags) {
        return respond(new SuggestOutcome.Success(List.of(tags)));
    }

    public FakeTagSuggester configured(boolean value) {
        this.configured = value;
        return this;
    }

    public FakeTagSuggester delay(Duration value) {
        this.delay = value;
        return this;
    }

    /** 다음 호출들을 {@link #release()}까지 멈춘다. 들어온 것은 {@link #awaitEntered()}로 기다린다. */
    public FakeTagSuggester hold() {
        this.entered = new CountDownLatch(1);
        this.gate = new CountDownLatch(1);
        return this;
    }

    public boolean awaitEntered() throws InterruptedException {
        CountDownLatch e = entered;
        return e != null && e.await(10, TimeUnit.SECONDS);
    }

    public void release() {
        CountDownLatch g = gate;
        if (g != null) {
            g.countDown();
        }
        gate = null;
        entered = null;
    }

    public int calls() {
        return calls.get();
    }

    public List<TagSuggestInput> inputs() {
        return List.copyOf(inputs);
    }

    public TagSuggestInput lastInput() {
        return inputs.get(inputs.size() - 1);
    }

    /** 처음 상태로 (기록·예정 응답·설정을 지운다). */
    public void reset() {
        release();
        synchronized (scripted) {
            scripted.clear();
        }
        inputs.clear();
        calls.set(0);
        configured = true;
        delay = Duration.ZERO;
    }
}
