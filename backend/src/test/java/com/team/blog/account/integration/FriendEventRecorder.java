package com.team.blog.account.integration;

import com.team.blog.shared.event.FriendAccepted;
import com.team.blog.shared.event.FriendRequested;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * test 프로필 전용: 발행된 친구 이벤트를 모은다. {@code @RecordApplicationEvents} 대신 쓴다 — 통합 테스트 컨텍스트를 하나로 유지하기
 * 위해서다(발행 순간 동기 수신, 커밋 여부와 무관).
 */
@Profile("test")
@Component
public class FriendEventRecorder {

    private final List<Object> events = new CopyOnWriteArrayList<>();

    @EventListener
    void onRequested(FriendRequested event) {
        events.add(event);
    }

    @EventListener
    void onAccepted(FriendAccepted event) {
        events.add(event);
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
