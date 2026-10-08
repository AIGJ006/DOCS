package com.team.blog.interaction.support;

import com.team.blog.shared.event.DomainEvent;
import com.team.blog.shared.event.MemberFollowed;
import com.team.blog.shared.event.MemberUnfollowed;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 010 팔로우 이벤트 수집기 (T013·T014). 커밋 후({@code AFTER_COMMIT}) 받은 {@link MemberFollowed}·{@link
 * MemberUnfollowed}를 모은다 — 롤백된 이벤트는 들어오지 않는다. 테스트 소스의 {@code @Profile("test") @Component}라 새 컨텍스트를
 * 만들지 않는다(009 {@code LikeEventProbe}와 같은 방식). {@link #arm()} 전에는 아무것도 모으지 않는다.
 */
@Profile("test")
@Component
public class FollowEventProbe {

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

    public long followed() {
        return events.stream().filter(MemberFollowed.class::isInstance).count();
    }

    public long unfollowed() {
        return events.stream().filter(MemberUnfollowed.class::isInstance).count();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onFollowed(MemberFollowed event) {
        if (armed) {
            events.add(event);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onUnfollowed(MemberUnfollowed event) {
        if (armed) {
            events.add(event);
        }
    }
}
