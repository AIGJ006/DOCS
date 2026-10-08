package com.team.blog.notification.support;

import com.team.blog.shared.event.DomainEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 테스트에서 이벤트를 업무 트랜잭션 안에서 발행하고 커밋한다 (011 T014). {@code AFTER_COMMIT} 리스너는 트랜잭션 밖 발행을 버리므로 이 도구로
 * 발행한다. 테스트 소스의 {@code @Component}라 공용 통합 테스트 컨텍스트에 함께 등록된다.
 */
@Profile("test")
@Component
public class EventPublisherHelper {

    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;

    public EventPublisherHelper(ApplicationEventPublisher events, TransactionTemplate tx) {
        this.events = events;
        this.tx = tx;
    }

    public void publish(DomainEvent... list) {
        tx.executeWithoutResult(
                status -> {
                    for (DomainEvent event : list) {
                        events.publishEvent(event);
                    }
                });
    }
}
