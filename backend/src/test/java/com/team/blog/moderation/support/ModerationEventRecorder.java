package com.team.blog.moderation.support;

import com.team.blog.shared.event.ContentHidden;
import com.team.blog.shared.event.ContentUnhidden;
import com.team.blog.shared.event.MemberSuspended;
import com.team.blog.shared.event.ReportResolved;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * test 프로필 전용: 014가 내는 이벤트를 모은다(tasks.md의 {@code RecordedEvents} 자리).
 * {@code @RecordApplicationEvents} 대신 쓴다 — 통합 테스트 컨텍스트를 하나로 유지하기 위해서다. 발행 순간 동기로 받으므로 롤백된 트랜잭션의
 * 이벤트도 들어온다.
 */
@Profile("test")
@Component
public class ModerationEventRecorder {

    private final List<Object> events = new CopyOnWriteArrayList<>();

    @EventListener
    void on(ReportResolved e) {
        events.add(e);
    }

    @EventListener
    void on(ContentHidden e) {
        events.add(e);
    }

    @EventListener
    void on(ContentUnhidden e) {
        events.add(e);
    }

    @EventListener
    void on(MemberSuspended e) {
        events.add(e);
    }

    public void clear() {
        events.clear();
    }

    public <T> List<T> of(Class<T> type) {
        return events.stream().filter(type::isInstance).map(type::cast).toList();
    }

    public List<Object> all() {
        return List.copyOf(events);
    }
}
