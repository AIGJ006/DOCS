package com.team.blog.account.web;

import com.team.blog.account.application.MeQueryService;
import com.team.blog.account.application.MeSummary;
import com.team.blog.account.application.PasswordChangeService;
import com.team.blog.account.web.dto.PasswordChangeRequest;
import com.team.blog.shared.error.ApiException;
import com.team.blog.shared.error.CommonReasonCode;
import com.team.blog.shared.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 내 정보 API ({@code /api/me/**}, 로그인 필요). */
@RestController
@RequestMapping("/api/me")
public class MeController {

    private final MeQueryService meQueryService;
    private final PasswordChangeService passwordChangeService;

    public MeController(
            MeQueryService meQueryService, PasswordChangeService passwordChangeService) {
        this.meQueryService = meQueryService;
        this.passwordChangeService = passwordChangeService;
    }

    /** 현재 로그인 상태 요약 ({@code getMe}). 비로그인이면 401. */
    @GetMapping
    public MeSummary me(@CurrentUser Long memberId) {
        return meQueryService.summary(memberId);
    }

    /**
     * 비밀번호 변경 ({@code changeMyPassword}) → 204. 다른 기기의 세션은 지우고 지금 세션은 남기되 세션 ID를 새로 발급한다(FR-045).
     */
    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(
            @CurrentUser Long memberId,
            @RequestBody PasswordChangeRequest body,
            HttpServletRequest request) {
        if (body == null) {
            throw new ApiException(CommonReasonCode.MALFORMED_REQUEST);
        }
        HttpSession session = request.getSession(false);
        passwordChangeService.change(
                memberId,
                body.currentPassword(),
                body.newPassword(),
                body.newPasswordConfirm(),
                session == null ? null : session.getId());
        if (session != null) {
            request.changeSessionId();
        }
    }
}
