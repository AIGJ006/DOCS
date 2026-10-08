package com.team.blog.interaction.web;

import com.team.blog.interaction.application.ViewProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 방문자 쿠키 필터 등록 (009 T033). 보안 필터 체인 뒤에 둬 로그인 여부를 안다. 서블릿 URL 패턴은 가운데 와일드카드를 못 쓰므로 {@code /*}에 걸고
 * 필터가 경로를 다시 본다(002 {@code AutosaveWebConfig}와 같은 방식).
 */
@Configuration(proxyBeanMethods = false)
public class ViewWebConfig {

    @Bean
    FilterRegistrationBean<VisitorIdCookieFilter> visitorIdCookieFilter(
            ViewProperties properties,
            @Value("${server.servlet.session.cookie.secure:true}") boolean secure) {
        FilterRegistrationBean<VisitorIdCookieFilter> registration =
                new FilterRegistrationBean<>(
                        new VisitorIdCookieFilter(properties.visitorCookieMaxAge(), secure));
        registration.addUrlPatterns("/*");
        registration.setOrder(SecurityFilterProperties.DEFAULT_FILTER_ORDER + 2);
        registration.setName("visitorIdCookieFilter");
        return registration;
    }
}
