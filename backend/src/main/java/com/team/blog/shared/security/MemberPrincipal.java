package com.team.blog.shared.security;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Optional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticatedPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 세션에 저장되는 로그인 회원. principal 이름 = 회원 번호 문자열이며, Spring Session 인덱스 저장소가 이 이름으로 "그 회원의 모든 세션"을
 * 찾는다(R-03, {@code SessionTerminator}).
 *
 * <p>계정 상태(인증 여부·정지·탈퇴)는 여기 두지 않는다. 쓰기 판정은 {@link AccountStatusGuard}가 매번 DB에서 읽는다(H7).
 *
 * @param memberId 회원 번호
 * @param role {@code USER} 또는 {@code ADMIN}
 */
public record MemberPrincipal(long memberId, String role)
        implements AuthenticatedPrincipal, Serializable {

    @Serial private static final long serialVersionUID = 1L;

    @Override
    public String getName() {
        return String.valueOf(memberId);
    }

    public List<GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    /** 이 회원으로 인증된 {@link Authentication}. */
    public static Authentication authenticated(long memberId, String role) {
        MemberPrincipal principal = new MemberPrincipal(memberId, role);
        return UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.authorities());
    }

    /** 현재 요청의 로그인 회원 (없으면 빈 값). */
    public static Optional<MemberPrincipal> current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null
                && auth.isAuthenticated()
                && auth.getPrincipal() instanceof MemberPrincipal p) {
            return Optional.of(p);
        }
        return Optional.empty();
    }
}
