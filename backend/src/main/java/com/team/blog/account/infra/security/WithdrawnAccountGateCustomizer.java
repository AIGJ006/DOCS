package com.team.blog.account.infra.security;

import com.team.blog.account.application.MemberQueryService;
import com.team.blog.shared.error.ErrorResponseWriter;
import com.team.blog.shared.security.SecurityFilterChainCustomizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.stereotype.Component;

/** {@link WithdrawnAccountGateFilter}를 보안 필터 체인의 {@link AuthorizationFilter} 뒤에 붙인다 (T042a). */
@Component
public class WithdrawnAccountGateCustomizer implements SecurityFilterChainCustomizer {

    private final MemberQueryService memberQueryService;
    private final ErrorResponseWriter errorWriter;

    public WithdrawnAccountGateCustomizer(
            MemberQueryService memberQueryService, ErrorResponseWriter errorWriter) {
        this.memberQueryService = memberQueryService;
        this.errorWriter = errorWriter;
    }

    @Override
    public void customize(HttpSecurity http) {
        http.addFilterAfter(
                new WithdrawnAccountGateFilter(memberQueryService, errorWriter),
                AuthorizationFilter.class);
    }
}
