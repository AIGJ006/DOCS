package com.team.blog.account.infra;

import com.team.blog.account.domain.MemberStatus;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 로그인 기록 한 문장 (FR-057, T145 이메일 로그인 SQL 수): {@code auth_identity.last_login_at}을 지금으로 바꾸면서 바꾸기
 * <b>전</b> 값과 회원 상태를 함께 돌려준다. 인증 수단·회원 엔티티를 따로 읽지 않는다.
 *
 * <p>{@code FROM auth_identity old}는 이 문장이 시작할 때의 행을 읽으므로 {@code old.last_login_at}이 갱신 전 값이다.
 */
@Repository
public class LoginStampRepository {

    /** 갱신 전 {@code last_login_at}(첫 로그인이면 null)과 회원 상태. */
    public record LoginStamp(Instant previousLoginAt, MemberStatus status) {}

    private final JdbcClient jdbc;

    public LoginStampRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<LoginStamp> record(long memberId, Instant now) {
        return jdbc.sql(
                        """
                        UPDATE auth_identity a
                           SET last_login_at = :now
                          FROM auth_identity old, member m
                         WHERE a.member_id = :memberId
                           AND old.id = a.id
                           AND m.id = a.member_id
                           AND m.deleted_at IS NULL
                        RETURNING old.last_login_at AS previous, m.status AS status
                        """)
                .param("now", now.atOffset(ZoneOffset.UTC))
                .param("memberId", memberId)
                .query(
                        (rs, n) -> {
                            Timestamp previous = rs.getTimestamp("previous");
                            return new LoginStamp(
                                    previous == null ? null : previous.toInstant(),
                                    MemberStatus.valueOf(rs.getString("status")));
                        })
                .optional();
    }
}
