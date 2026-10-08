package com.team.blog.account.infra.security;

import com.team.blog.account.application.MemberQueryService;
import com.team.blog.shared.error.ErrorResponseWriter;
import com.team.blog.shared.security.SecurityFilterChainCustomizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.stereotype.Component;

/**
 * 요청 단계 계정 게이트를 보안 필터 체인의 {@link AuthorizationFilter} 뒤에 붙인다: {@link WithdrawnAccountGateFilter}
 * (T042a) → {@link ReagreementGateFilter}(US5 T109). 탈퇴 유예 안내가 재동의보다 먼저다.
 */
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
        http.addFilterAfter(
                new ReagreementGateFilter(errorWriter), WithdrawnAccountGateFilter.class);
    }
}
