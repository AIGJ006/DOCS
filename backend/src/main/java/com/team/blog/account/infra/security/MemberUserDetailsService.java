package com.team.blog.account.infra.security;

import com.team.blog.account.application.EmailAddress;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * 이메일 로그인 조회 (R-04): LOCAL {@code provider_user_id} = 앞뒤 공백 제거·소문자 이메일({@code uq_auth_identity})
 * 1번. 익명 처리(015)된 회원은 없는 것으로 본다. 소셜 계정은 비밀번호가 없어 찾지 않는다.
 *
 * <p>Bean이 아니다 — {@link MemberAuthenticationProvider}만 쓴다(Spring Security가 기본 인증 제공자를 따로 만들지 않게).
 */
final class MemberUserDetailsService implements UserDetailsService {

    private final JdbcClient jdbc;

    MemberUserDetailsService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        String email = EmailAddress.normalize(username);
        List<MemberUserDetails> rows =
                jdbc.sql(
                                """
                                SELECT a.member_id, a.password_hash, m.role
                                  FROM auth_identity a
                                  JOIN member m ON m.id = a.member_id
                                 WHERE a.provider = 'LOCAL' AND a.provider_user_id = :email
                                   AND m.deleted_at IS NULL
                                """)
                        .param("email", email)
                        .query(
                                (rs, n) ->
                                        new MemberUserDetails(
                                                rs.getLong("member_id"),
                                                rs.getString("role"),
                                                email,
                                                rs.getString("password_hash")))
                        .list();
        if (rows.isEmpty()) {
            throw new UsernameNotFoundException("not found");
        }
        return rows.getFirst();
    }
}
