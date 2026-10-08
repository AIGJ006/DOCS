package com.team.blog.notification.support;

import com.team.blog.notification.application.NotificationWriter;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * {@link NotificationWriter}가 예외를 던지게 바꾸는 테스트 스위치 (011 T018, SC-009). {@code @MockitoSpyBean} 대신 공용
 * 통합 테스트 컨텍스트에 함께 등록되는 감싸기라 새 컨텍스트를 만들지 않는다(PostgreSQL 연결 수). 켜지 않으면 그대로 통과한다.
 */
@Profile("test")
@Component
public class NotificationWriterFailureSwitch implements BeanPostProcessor {

    private static volatile boolean failing;

    public static void fail(boolean on) {
        failing = on;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (!(bean instanceof NotificationWriter)) {
            return bean;
        }
        ProxyFactory factory = new ProxyFactory(bean);
        factory.setProxyTargetClass(true);
        factory.addAdvice(
                (MethodInterceptor)
                        invocation -> {
                            if (failing) {
                                throw new IllegalStateException("시험용 알림 저장 실패");
                            }
                            return invocation.proceed();
                        });
        return factory.getProxy();
    }
}
