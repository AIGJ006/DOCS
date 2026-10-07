package com.team.blog.account.infra.security;

import com.team.blog.shared.security.MemberPrincipal;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 이메일·비밀번호 인증 (Spring Security 폼 로그인, BCrypt — R-04·R-14). 인증에 성공하면 principal을 {@link
 * MemberPrincipal}(회원 번호·역할)로 바꾼다 — 세션 인덱스의 principal 이름이 회원 번호가 되어 {@code SessionTerminator}가 그
 * 회원의 세션을 찾는다(R-03).
 *
 * <p>없는 이메일도 BCrypt 비교 시간을 들여 응답 시간으로 가입 여부를 알 수 없게 한다(DaoAuthenticationProvider 기본 동작).
 */
@Component
public class MemberAuthenticationProvider extends DaoAuthenticationProvider {

    public MemberAuthenticationProvider(JdbcClient jdbc, PasswordEncoder passwordEncoder) {
        super(new MemberUserDetailsService(jdbc));
        setPasswordEncoder(passwordEncoder);
    }

    @Override
    protected Authentication createSuccessAuthentication(
            Object principal, Authentication authentication, UserDetails user) {
        MemberUserDetails member = (MemberUserDetails) user;
        Authentication result = MemberPrincipal.authenticated(member.memberId(), member.role());
        if (result instanceof UsernamePasswordAuthenticationToken token) {
            token.setDetails(authentication.getDetails());
        }
        return result;
    }
}
