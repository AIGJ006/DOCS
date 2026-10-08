package com.team.blog.notification.support;

import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.fixture.PostFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 알림 통합 테스트 공통 (011). 앞 테스트의 이벤트가 늦게 처리돼 이 테스트의 새 행에 알림을 만들지 않도록, 시작할 때 실행기가 빌 때까지 기다린 뒤 알림 테이블을 한
 * 번 더 비우고, 끝날 때도 실행기가 빌 때까지 기다린다. 공용 통합 테스트 컨텍스트를 그대로 쓴다.
 */
public abstract class NotificationTestBase extends IntegrationTestBase {

    @Autowired
    @Qualifier("eventExecutor")
    protected ThreadPoolTaskExecutor eventExecutor;

    @Autowired protected EventPublisherHelper eventPublisher;

    protected NotificationFixtures notifications;
    protected NotificationAwait awaiter;
    protected PostFixtures posts;

    @BeforeEach
    void settleNotificationEvents() {
        notifications = new NotificationFixtures(jdbc);
        awaiter = new NotificationAwait(jdbc, eventExecutor);
        posts = new PostFixtures(jdbc);
        awaiter.idle();
        jdbc.execute("DELETE FROM notification_actor");
        jdbc.execute("DELETE FROM notification");
        jdbc.execute("DELETE FROM notification_mute");
    }

    @AfterEach
    void drainNotificationEvents() {
        awaiter.idle();
    }
}
