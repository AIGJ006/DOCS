package com.team.blog.shared.security;

import com.team.blog.shared.error.ApiException;
import com.team.blog.shared.error.CommonReasonCode;
import java.util.Optional;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** {@link CurrentUser} 파라미터 처리: SecurityContext의 {@link MemberPrincipal}에서만 회원 번호를 꺼낸다. */
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        if (!parameter.hasParameterAnnotation(CurrentUser.class)) {
            return false;
        }
        Class<?> type = parameter.getParameterType();
        return type == Long.class || type == long.class || type == Optional.class;
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {
        Optional<Long> memberId = MemberPrincipal.current().map(MemberPrincipal::memberId);
        if (parameter.getParameterType() == Optional.class) {
            return memberId;
        }
        CurrentUser annotation = parameter.getParameterAnnotation(CurrentUser.class);
        boolean required =
                annotation == null
                        || annotation.required()
                        || parameter.getParameterType() == long.class;
        if (memberId.isEmpty() && required) {
            throw new ApiException(CommonReasonCode.LOGIN_REQUIRED);
        }
        return memberId.orElse(null);
    }
}
