package com.team.blog.shared.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 시간 Bean. 저장·비교는 UTC {@link Clock}(테스트에서 고정 시계로 바꿀 수 있음), 날짜 경계·표시는 서비스 시간대 {@link ZoneId} ({@code
 * blog.time-zone}, 기본 Asia/Seoul).
 */
@Configuration(proxyBeanMethods = false)
public class TimeConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    ZoneId serviceZoneId(CoreProperties properties) {
        return properties.timeZone();
    }
}
