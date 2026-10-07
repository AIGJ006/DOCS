package com.team.blog.post.config;

import com.team.blog.post.web.AutosaveRequestSizeFilter;
import com.team.blog.shared.error.ErrorResponseWriter;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 자동 저장 요청 크기 제한 필터 등록 (002 T080). 보안 필터 체인({@code SecurityFilterProperties.DEFAULT_FILTER_ORDER})
 * 바로 뒤에 둔다. 서블릿 URL 패턴은 가운데 와일드카드를 못 쓰므로 {@code /api/posts/*}에 걸고 필터가 경로를 다시 본다.
 */
@Configuration(proxyBeanMethods = false)
public class AutosaveWebConfig {

    @Bean
    FilterRegistrationBean<AutosaveRequestSizeFilter> autosaveRequestSizeFilter(
            PostAuthoringProperties properties, ErrorResponseWriter errors) {
        FilterRegistrationBean<AutosaveRequestSizeFilter> registration =
                new FilterRegistrationBean<>(
                        new AutosaveRequestSizeFilter(
                                properties.autosave().maxRequestBytes(), errors));
        registration.addUrlPatterns("/api/posts/*");
        registration.setOrder(SecurityFilterProperties.DEFAULT_FILTER_ORDER + 1);
        registration.setName("autosaveRequestSizeFilter");
        return registration;
    }
}
