package com.team.blog.account.infra.security;

import java.io.Serial;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

/**
 * 이메일 로그인용 사용자 정보. 인증이 끝나면 {@link MemberAuthenticationProvider}가 세션에는 {@code MemberPrincipal}만 남긴다
 * (비밀번호 해시가 세션에 들어가지 않음).
 */
final class MemberUserDetails extends User {

    @Serial private static final long serialVersionUID = 1L;

    private final long memberId;
    private final String role;

    MemberUserDetails(long memberId, String role, String email, String passwordHash) {
        super(email, passwordHash, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        this.memberId = memberId;
        this.role = role;
    }

    long memberId() {
        return memberId;
    }

    String role() {
        return role;
    }
}
