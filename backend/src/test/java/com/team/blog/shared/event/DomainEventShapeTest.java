package com.team.blog.shared.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

/**
 * 도메인 이벤트 모양 검사 (011 T007, SC-011, 20 EV-3). {@code shared.event}의 {@link DomainEvent} 구현은 모두
 * record이고 필드는 {@code long}·{@code Long}·{@link Instant}·enum만이다(글자·컬렉션·엔티티 금지). 다른 기능이 이벤트를 더하면
 * 자동으로 검사된다.
 */
class DomainEventShapeTest {

    private static final Set<Class<?>> ALLOWED = Set.of(long.class, Long.class, Instant.class);

    @Test
    void 모든_이벤트는_ID_enum_시각만_싣는다() throws Exception {
        List<Class<?>> events = events();
        assertThat(events)
                .as("이벤트가 하나도 없으면 검사 범위가 틀린 것")
                .extracting(Class::getSimpleName)
                .contains("PostPublished", "PostEdited", "PostWentPublic");

        List<String> violations = new ArrayList<>();
        for (Class<?> event : events) {
            if (!event.isRecord()) {
                violations.add(event.getSimpleName() + ": record가 아님");
                continue;
            }
            for (RecordComponent component : event.getRecordComponents()) {
                Class<?> type = component.getType();
                if (!ALLOWED.contains(type) && !type.isEnum()) {
                    violations.add(
                            event.getSimpleName()
                                    + "."
                                    + component.getName()
                                    + ": "
                                    + type.getSimpleName());
                }
            }
        }
        assertThat(violations).as("허용되지 않은 이벤트 필드").isEmpty();
    }

    private static List<Class<?>> events() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(DomainEvent.class));
        List<Class<?>> result = new ArrayList<>();
        for (BeanDefinition candidate :
                scanner.findCandidateComponents(DomainEvent.class.getPackageName())) {
            Class<?> type = Class.forName(candidate.getBeanClassName());
            if (type != DomainEvent.class && DomainEvent.class.isAssignableFrom(type)) {
                result.add(type);
            }
        }
        return result;
    }
}
