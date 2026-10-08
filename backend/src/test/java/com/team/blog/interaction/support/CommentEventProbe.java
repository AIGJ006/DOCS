package com.team.blog.interaction.support;

import com.team.blog.shared.event.CommentCreated;
import com.team.blog.shared.event.CommentDeleted;
import com.team.blog.shared.event.DomainEvent;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 007 댓글 이벤트 수집기 (테스트 전용). 커밋 후 받은 {@link CommentCreated}·{@link CommentDeleted}를 모은다. 테스트 소스의
 * {@code @Component}라 공용 통합 테스트 컨텍스트에 함께 등록된다(새 컨텍스트를 만들지 않는다). {@link #arm()} 전에는 기록하지 않는다.
 */
@Profile("test")
@Component
public class CommentEventProbe {

    private final List<DomainEvent> events = new CopyOnWriteArrayList<>();
    private volatile boolean armed;

    public void arm() {
        events.clear();
        armed = true;
    }

    public void reset() {
        armed = false;
        events.clear();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onCreated(CommentCreated event) {
        if (armed) {
            events.add(event);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onDeleted(CommentDeleted event) {
        if (armed) {
            events.add(event);
        }
    }

    public <T extends DomainEvent> List<T> of(Class<T> type) {
        return events.stream().filter(type::isInstance).map(type::cast).toList();
    }
}
