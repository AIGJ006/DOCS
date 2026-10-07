package com.team.blog.account.application;

import com.team.blog.account.domain.MemberStatus;
import com.team.blog.shared.error.AccountStateException;
import com.team.blog.shared.error.ApiException;
import com.team.blog.shared.error.CommonReasonCode;
import com.team.blog.shared.security.AccountStatusGuard;
import com.team.blog.shared.security.ActionKind;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** {@link AccountStatusGuard} 구현. {@code member} JOIN {@code auth_identity} 1번 조회로 매번 DB에서 판정한다. */
@Service
public class AccountStatusGuardService implements AccountStatusGuard {

    /** 탈퇴 유예 회원에게 복구 화면으로 안내하라는 표시 (004 contracts {@code details.action}). */
    public static final Map<String, Object> RESTORE_DETAILS = Map.of("action", "RESTORE");

    private final JdbcClient jdbc;

    public AccountStatusGuardService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private record State(MemberStatus status, boolean deleted, boolean emailVerified) {}

    @Override
    public void requireActive(long memberId, ActionKind kind) {
        List<State> rows =
                jdbc.sql(
                                """
                                SELECT m.status, m.deleted_at IS NOT NULL AS deleted,
                                       a.email_verified_at IS NOT NULL AS verified
                                  FROM member m
                                  LEFT JOIN auth_identity a ON a.member_id = m.id
                                 WHERE m.id = ?
                                """)
                        .param(memberId)
                        .query(
                                (rs, n) ->
                                        new State(
                                                MemberStatus.valueOf(rs.getString("status")),
                                                rs.getBoolean("deleted"),
                                                rs.getBoolean("verified")))
                        .list();
        if (rows.isEmpty() || rows.getFirst().deleted()) {
            throw new ApiException(CommonReasonCode.LOGIN_REQUIRED);
        }
        State state = rows.getFirst();
        switch (state.status()) {
            case WITHDRAWN ->
                    throw new AccountStateException(
                            CommonReasonCode.ACCOUNT_WITHDRAWN, RESTORE_DETAILS);
            case SUSPENDED -> throw new AccountStateException(CommonReasonCode.ACCOUNT_SUSPENDED);
            case ACTIVE -> {
                if (kind.requiresVerifiedEmail() && !state.emailVerified()) {
                    throw new AccountStateException(CommonReasonCode.EMAIL_NOT_VERIFIED);
                }
            }
        }
    }
}
