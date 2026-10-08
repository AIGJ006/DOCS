package com.team.blog.account.application;

import com.team.blog.account.application.WithdrawalPreview.Verification;
import com.team.blog.account.application.port.AuthoredCommentStats;
import com.team.blog.account.application.port.AuthoredPostStats;
import com.team.blog.shared.error.ApiException;
import com.team.blog.shared.error.CommonReasonCode;
import com.team.blog.shared.security.AccountStatusGuard;
import com.team.blog.shared.security.ActionKind;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * 탈퇴 안내 숫자 (015 T023, research R7, FR-003). 판정: 401 → 403(유예 게이트, {@code ACCOUNT_WRITE} 가드 — 인증 전
 * 통과·정지 403). 관리자도 숫자는 볼 수 있다(신청 때 409). SQL 3번: 회원·로그인 수단 1번 + 포트 2개.
 */
@Service
public class WithdrawalPreviewService {

    private final AccountStatusGuard statusGuard;
    private final JdbcClient jdbc;
    private final AuthoredPostStats postStats;
    private final AuthoredCommentStats commentStats;
    private final WithdrawalPolicy policy;

    public WithdrawalPreviewService(
            AccountStatusGuard statusGuard,
            JdbcClient jdbc,
            AuthoredPostStats postStats,
            AuthoredCommentStats commentStats,
            WithdrawalPolicy policy) {
        this.statusGuard = statusGuard;
        this.jdbc = jdbc;
        this.postStats = postStats;
        this.commentStats = commentStats;
        this.policy = policy;
    }

    private record Owner(String handle, String provider) {}

    public WithdrawalPreview preview(long memberId) {
        statusGuard.requireActive(memberId, ActionKind.ACCOUNT_WRITE);
        Owner owner =
                jdbc.sql(
                                """
                                SELECT m.handle, a.provider FROM member m
                                  JOIN auth_identity a ON a.member_id = m.id
                                 WHERE m.id = :m AND m.deleted_at IS NULL
                                """)
                        .param("m", memberId)
                        .query(
                                (rs, n) ->
                                        new Owner(rs.getString("handle"), rs.getString("provider")))
                        .optional()
                        .orElseThrow(() -> new ApiException(CommonReasonCode.LOGIN_REQUIRED));
        AuthoredPostStats.PostStats posts = postStats.statsOf(memberId);
        long comments = commentStats.countOnOthersPosts(memberId);
        return new WithdrawalPreview(
                owner.handle(),
                posts.postCount(),
                comments,
                posts.receivedLikeCount(),
                policy.deadline(policy.now()),
                "LOCAL".equals(owner.provider())
                        ? Verification.PASSWORD
                        : Verification.CONFIRM_TEXT);
    }
}
