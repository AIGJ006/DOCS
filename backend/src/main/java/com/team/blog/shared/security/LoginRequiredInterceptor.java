package com.team.blog.shared.security;

import com.team.blog.shared.error.ApiException;
import com.team.blog.shared.error.CommonReasonCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/** {@link LoginRequired}가 붙은 핸들러를 비로그인이 부르면 401 {@code LOGIN_REQUIRED}. */
public class LoginRequiredInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod method
                && (method.hasMethodAnnotation(LoginRequired.class)
                        || AnnotatedElementUtils.hasAnnotation(
                                method.getBeanType(), LoginRequired.class))
                && MemberPrincipal.current().isEmpty()) {
            throw new ApiException(CommonReasonCode.LOGIN_REQUIRED);
        }
        return true;
    }
}
