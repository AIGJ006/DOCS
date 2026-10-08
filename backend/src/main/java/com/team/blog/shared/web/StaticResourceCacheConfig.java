package com.team.blog.shared.web;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * React 빌드 정적 파일의 {@code Cache-Control} (016 research R10, contracts/theme.md §8).
 *
 * <ul>
 *   <li>{@code /js/**}({@code theme-init.js}): 이름에 해시가 없어 {@link
 *       CacheControlPolicy#STATIC_REVALIDATE} + ETag· Last-Modified. 배포하면 바로 새 파일을 쓴다.
 *   <li>{@code /assets/**}(Vite 해시 파일): {@link CacheControlPolicy#STATIC_IMMUTABLE}.
 * </ul>
 *
 * 그 밖의 경로(SPA 셸 {@code index.html} 등)는 Spring Boot 기본 정적 처리와 각 컨트롤러가 정한 머리를 그대로 둔다.
 */
@Configuration(proxyBeanMethods = false)
public class StaticResourceCacheConfig implements WebMvcConfigurer {

    static final String LOCATION = "classpath:/static/";

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/js/**")
                .addResourceLocations(LOCATION + "js/")
                .setCacheControl(CacheControl.noCache())
                .setEtagGenerator(StaticResourceCacheConfig::etag);
        registry.addResourceHandler("/assets/**")
                .addResourceLocations(LOCATION + "assets/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).immutable());
    }

    /** 크기·수정 시각으로 만드는 약한 ETag. 파일을 다시 읽지 않는다. */
    static String etag(Resource resource) {
        try {
            return "W/\"" + resource.contentLength() + "-" + resource.lastModified() + "\"";
        } catch (IOException ex) {
            return null;
        }
    }
}
