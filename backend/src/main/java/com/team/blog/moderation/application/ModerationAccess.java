package com.team.blog.moderation.application;

import com.team.blog.shared.error.ApiException;
import com.team.blog.shared.error.CommonReasonCode;
import com.team.blog.shared.error.NotFoundException;
import com.team.blog.shared.security.AccountStatusGuard;
import com.team.blog.shared.security.ActionKind;
import com.team.blog.shared.security.Viewer;
import org.springframework.stereotype.Component;

/**
 * 관리자 API의 두 번째 겹 (014 T037, 헌법 III, research R5). 004 관리자 경로 규칙이 일반 회원을 먼저 404로 막지만, 모든 관리자
 * Service는 첫머리에서 이것으로 다시 확인한다: 비회원 401 → 관리자 아님 404(고정 본문) → 계정 상태({@code CONTENT_WRITE}) 403.
 */
@Component
public class ModerationAccess {

    private final AccountStatusGuard accountStatusGuard;

    public ModerationAccess(AccountStatusGuard accountStatusGuard) {
        this.accountStatusGuard = accountStatusGuard;
    }

    /**
     * @return 관리자 회원 번호
     */
    public long requireAdmin(Viewer viewer) {
        if (viewer == null || !viewer.isAuthenticated()) {
            throw new ApiException(CommonReasonCode.LOGIN_REQUIRED);
        }
        if (!viewer.isAdmin()) {
            throw new NotFoundException("관리자 API: 관리자 아님");
        }
        accountStatusGuard.requireActive(viewer.id(), ActionKind.CONTENT_WRITE);
        return viewer.id();
    }
}
