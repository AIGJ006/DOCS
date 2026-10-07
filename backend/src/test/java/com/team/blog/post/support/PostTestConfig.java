package com.team.blog.post.support;

import com.team.blog.shared.event.DomainEvent;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 002 통합 테스트 설정 (T009). 필요한 테스트 클래스만 {@code @Import(PostTestConfig.class)}로 쓴다(컴포넌트 스캔 대상 아님).
 *
 * <ul>
 *   <li>{@link StubImageReferenceResolver}: 실제 media 어댑터 대신 쓰는 사진 판별기({@code @Primary}, {@code
 *       ImageReferenceResolver}로 주입해도 이것). 소유 키는 테스트가 지정한다.
 *   <li>{@link CommittedEvents}: 커밋 후({@code AFTER_COMMIT}) 받은 도메인 이벤트를 모은다. 롤백된 이벤트는 들어오지 않는다.
 * </ul>
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostTestConfig {

    @Bean
    @Primary
    StubImageReferenceResolver stubImageReferenceResolver() {
        return new StubImageReferenceResolver();
    }

    @Bean
    CommittedEvents committedEvents() {
        return new CommittedEvents();
    }

    /** 커밋 후 이벤트 수집 리스너. 테스트마다 {@link #clear()}. */
    public static class CommittedEvents {

        private final List<DomainEvent> events = new CopyOnWriteArrayList<>();

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        void on(DomainEvent event) {
            events.add(event);
        }

        public List<DomainEvent> all() {
            return List.copyOf(events);
        }

        public <T extends DomainEvent> List<T> of(Class<T> type) {
            return events.stream().filter(type::isInstance).map(type::cast).toList();
        }

        public void clear() {
            events.clear();
        }
    }
}
