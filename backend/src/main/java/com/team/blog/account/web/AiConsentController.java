package com.team.blog.account.web;

import com.team.blog.account.application.AiConsentService;
import com.team.blog.account.application.AiConsentView;
import com.team.blog.account.web.dto.AiConsentRequest;
import com.team.blog.shared.security.AccountStatusGuard;
import com.team.blog.shared.security.ActionKind;
import com.team.blog.shared.security.CurrentUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 외부 전송 동의 (013 US2, {@code getAiConsent}·{@code agreeAi}·{@code revokeAi}). 바꾸는 요청은 계정 상태
 * {@code ACCOUNT_WRITE}(인증 전 회원 통과, 정지 회원 403).
 */
@RestController
@RequestMapping("/api/me/agreements/ai")
public class AiConsentController {

    private final AiConsentService consentService;
    private final AccountStatusGuard statusGuard;

    public AiConsentController(AiConsentService consentService, AccountStatusGuard statusGuard) {
        this.consentService = consentService;
        this.statusGuard = statusGuard;
    }

    @GetMapping
    public AiConsentView get(@CurrentUser Long memberId) {
        return consentService.view(memberId);
    }

    @PutMapping
    public AiConsentView agree(
            @CurrentUser Long memberId, @RequestBody(required = false) AiConsentRequest body) {
        statusGuard.requireActive(memberId, ActionKind.ACCOUNT_WRITE);
        return consentService.agree(memberId, body == null ? null : body.version());
    }

    @DeleteMapping
    public AiConsentView revoke(@CurrentUser Long memberId) {
        statusGuard.requireActive(memberId, ActionKind.ACCOUNT_WRITE);
        return consentService.revoke(memberId);
    }
}
