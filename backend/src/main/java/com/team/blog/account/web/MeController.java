package com.team.blog.account.web;

import com.team.blog.account.application.AccountSettingsService;
import com.team.blog.account.application.AgreementService;
import com.team.blog.account.application.MeQueryService;
import com.team.blog.account.application.MeSummary;
import com.team.blog.account.application.PasswordChangeService;
import com.team.blog.account.application.PreviousLogin;
import com.team.blog.account.application.ProfileService;
import com.team.blog.account.infra.security.LoginSession;
import com.team.blog.account.web.dto.AgreementConsent;
import com.team.blog.account.web.dto.MyProfileResponse;
import com.team.blog.account.web.dto.MySettingsResponse;
import com.team.blog.account.web.dto.PasswordChangeRequest;
import com.team.blog.account.web.dto.ProfileUpdateRequest;
import com.team.blog.account.web.dto.SettingsUpdateRequest;
import com.team.blog.shared.error.ApiException;
import com.team.blog.shared.error.CommonReasonCode;
import com.team.blog.shared.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
    private final AgreementService agreementService;
    private final ProfileService profileService;
    private final AccountSettingsService settingsService;

    public MeController(
            MeQueryService meQueryService,
            PasswordChangeService passwordChangeService,
            AgreementService agreementService,
            ProfileService profileService,
            AccountSettingsService settingsService) {
        this.meQueryService = meQueryService;
        this.passwordChangeService = passwordChangeService;
        this.agreementService = agreementService;
        this.profileService = profileService;
        this.settingsService = settingsService;
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

    /**
     * 약관·처리방침 재동의 ({@code reagree}) → 204. 현재 버전으로 동의를 갱신하고 세션의 {@code reagreementRequired} 표시를
     * 지운다(FR-012). 재동의 게이트의 허용 목록에 있다.
     */
    @PutMapping("/agreements")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reagree(
            @CurrentUser Long memberId,
            @RequestBody AgreementConsent body,
            HttpServletRequest request) {
        if (body == null) {
            throw new ApiException(CommonReasonCode.MALFORMED_REQUEST);
        }
        agreementService.reagree(memberId, body.toVersions());
        LoginSession.clearReagreement(request.getSession(false));
    }

    /** 내 프로필 ({@code getMyProfile}). */
    @GetMapping("/profile")
    public MyProfileResponse profile(@CurrentUser Long memberId) {
        return MyProfileResponse.of(profileService.get(memberId));
    }

    /**
     * 프로필 저장 ({@code updateMyProfile}) — 닉네임·소개·사진을 한 번에. 하나라도 실패하면 아무것도 저장하지 않는다(SC-008). 대상은 로그인한
     * 본인뿐이다.
     */
    @PatchMapping("/profile")
    public MyProfileResponse updateProfile(
            @CurrentUser Long memberId, @RequestBody ProfileUpdateRequest body) {
        if (body == null) {
            throw new ApiException(CommonReasonCode.MALFORMED_REQUEST);
        }
        return MyProfileResponse.of(profileService.update(memberId, body.toCommand()));
    }

    /** 계정 설정 ({@code getMySettings}). */
    @GetMapping("/settings")
    public MySettingsResponse settings(@CurrentUser Long memberId, HttpServletRequest request) {
        return MySettingsResponse.of(settingsService.get(memberId, previousLogin(request)));
    }

    /** 새 글 기본 공개 범위·최근 활동 공개 변경 ({@code updateMySettings}). */
    @PatchMapping("/settings")
    public MySettingsResponse updateSettings(
            @CurrentUser Long memberId,
            @RequestBody SettingsUpdateRequest body,
            HttpServletRequest request) {
        if (body == null) {
            throw new ApiException(CommonReasonCode.MALFORMED_REQUEST);
        }
        return MySettingsResponse.of(
                settingsService.update(memberId, body.toCommand(), previousLogin(request)));
    }

    /** 세션의 직전 로그인 — US8(T141)에서 채운다. 지금은 null("첫 로그인"). */
    private static PreviousLogin previousLogin(HttpServletRequest request) {
        return null;
    }
}
