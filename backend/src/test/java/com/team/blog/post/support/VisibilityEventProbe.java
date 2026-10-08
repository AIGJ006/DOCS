package com.team.blog.post.support;

import com.team.blog.shared.event.DomainEvent;
import com.team.blog.shared.event.PostVisibilityChanged;
import com.team.blog.shared.event.PostWentPublic;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 004 공개 범위 변경 이벤트 시험용 수집기 (T029). 커밋 후({@code AFTER_COMMIT}) 받은 {@link
 * PostVisibilityChanged}·{@link PostWentPublic}을 모은다 — 롤백된 이벤트는 들어오지 않는다. 테스트 소스의
 * {@code @Component}라 모든 통합 테스트 컨텍스트에 함께 등록된다(006 {@code PurgeStepProbe}와 같은 이유: 새 컨텍스트·연결 풀을 만들지
 * 않는다). {@link #arm()} 전에는 아무것도 하지 않는다.
 *
 * <p>{@link #failAfterRecording()}이면 기록한 뒤 예외를 던져 "리스너가 실패해도 변경 응답은 성공"(Constitution V)을 확인한다.
 */
@Profile("test")
@Component
public class VisibilityEventProbe {

    private final List<DomainEvent> events = new CopyOnWriteArrayList<>();
    private volatile boolean armed;
    private volatile boolean failing;

    /** 기록을 시작한다 (앞선 기록·실패 설정은 지운다). */
    public void arm() {
        events.clear();
        failing = false;
        armed = true;
    }

    /** 기록을 멈추고 지운다. */
    public void reset() {
        armed = false;
        failing = false;
        events.clear();
    }

    /** 이후 이벤트마다 기록한 뒤 예외를 던진다. */
    public void failAfterRecording() {
        failing = true;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onChanged(PostVisibilityChanged event) {
        record(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onWentPublic(PostWentPublic event) {
        record(event);
    }

    private void record(DomainEvent event) {
        if (!armed) {
            return;
        }
        events.add(event);
        if (failing) {
            throw new IllegalStateException("시험용 리스너 실패: " + event);
        }
    }

    public <T extends DomainEvent> List<T> of(Class<T> type) {
        return events.stream().filter(type::isInstance).map(type::cast).toList();
    }

    public List<DomainEvent> all() {
        return List.copyOf(events);
    }
}
