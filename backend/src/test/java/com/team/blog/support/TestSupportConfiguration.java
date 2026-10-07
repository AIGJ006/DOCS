package com.team.blog.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/**
 * 통합 테스트 공용 Bean: 메일 캡처, 테스트 로그인(`POST /test/login-as/{memberId}`), 보안 기반 확인용 테스트 컨트롤러.
 *
 * <p>{@link IntegrationTestBase}가 가져오므로 모든 통합 테스트가 같은 Spring 컨텍스트를 재사용한다.
 */
@TestConfiguration(proxyBeanMethods = false)
@Import({TestLoginController.class, ProbeController.class})
public class TestSupportConfiguration {

    @Bean
    @Primary
    CapturingMailSender capturingMailSender() {
        return new CapturingMailSender();
    }
}
