package com.team.blog.interaction.support;

import com.team.blog.shared.event.DomainEvent;
import com.team.blog.shared.event.PostLiked;
import com.team.blog.shared.event.PostUnliked;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 009 좋아요 이벤트 수집기 (T013). 커밋 후({@code AFTER_COMMIT}) 받은 {@link PostLiked}·{@link PostUnliked}를 모은다
 * — 롤백된 이벤트는 들어오지 않는다. 테스트 소스의 {@code @Profile("test") @Component}라 새 컨텍스트를 만들지 않는다. {@link #arm()}
 * 전에는 아무것도 모으지 않는다.
 */
@Profile("test")
@Component
public class LikeEventProbe {

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

    public List<DomainEvent> events() {
        return List.copyOf(events);
    }

    public long liked() {
        return events.stream().filter(PostLiked.class::isInstance).count();
    }

    public long unliked() {
        return events.stream().filter(PostUnliked.class::isInstance).count();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onLiked(PostLiked event) {
        if (armed) {
            events.add(event);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onUnliked(PostUnliked event) {
        if (armed) {
            events.add(event);
        }
    }
}
