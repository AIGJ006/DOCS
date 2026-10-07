package com.team.blog.shared.security;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** {@link Viewer} 컨트롤러 파라미터 처리기({@link CurrentViewerResolver})를 등록한다 (004 T011). */
@Configuration(proxyBeanMethods = false)
public class ViewerWebMvcConfig implements WebMvcConfigurer {

    private final CurrentViewerResolver currentViewerResolver;

    public ViewerWebMvcConfig(CurrentViewerResolver currentViewerResolver) {
        this.currentViewerResolver = currentViewerResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentViewerResolver);
    }
}
