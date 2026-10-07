package com.team.blog.account.infra.security;

import com.team.blog.account.application.LoginOutcome;
import com.team.blog.account.domain.Provider;
import com.team.blog.shared.security.MemberPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

/**
 * 폼 로그인 밖에서 로그인 상태를 만든다(가입 직후 — openapi {@code signupWithEmail}, 소셜 가입 마무리 US2). 세션이 이미 있으면 세션 ID를
 * 새로 발급하고(세션 고정 방지, FR-034), SecurityContext를 세션에 저장하고, 로그인 세션 속성을 담는다.
 */
@Component
public class SessionLogin {

    private final SecurityContextHolderStrategy contextHolder =
            SecurityContextHolder.getContextHolderStrategy();
    private final SecurityContextRepository contextRepository =
            new HttpSessionSecurityContextRepository();

    public void login(
            long memberId,
            String role,
            Provider provider,
            LoginOutcome outcome,
            HttpServletRequest request,
            HttpServletResponse response) {
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        SecurityContext context = contextHolder.createEmptyContext();
        context.setAuthentication(MemberPrincipal.authenticated(memberId, role));
        contextHolder.setContext(context);
        HttpSession session = request.getSession(true);
        contextRepository.saveContext(context, request, response);
        LoginSession.record(session, provider, outcome);
    }
}
