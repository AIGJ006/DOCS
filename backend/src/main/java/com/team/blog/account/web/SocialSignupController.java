package com.team.blog.account.web;

import com.team.blog.account.application.LoginOutcome;
import com.team.blog.account.application.LoginService;
import com.team.blog.account.application.PendingSocialSignup;
import com.team.blog.account.application.SignedUpMember;
import com.team.blog.account.application.SignupService;
import com.team.blog.account.application.SocialLoginService;
import com.team.blog.account.application.SocialSignupCommand;
import com.team.blog.account.infra.security.SessionLogin;
import com.team.blog.account.infra.security.SocialClientRegistrations;
import com.team.blog.account.infra.security.SocialLoginSession;
import com.team.blog.account.web.dto.SocialProvidersResponse;
import com.team.blog.account.web.dto.SocialSignupDraftResponse;
import com.team.blog.account.web.dto.SocialSignupRequest;
import com.team.blog.account.web.dto.SocialSignupResult;
import com.team.blog.shared.error.ApiException;
import com.team.blog.shared.error.CommonReasonCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 소셜 가입 마무리 API (contracts {@code social} 태그, R-07). 시작·콜백({@code /oauth2/authorization/*}·{@code
 * /login/oauth2/code/*})은 Spring Security가 처리한다({@code AccountSecurityCustomizer}).
 */
@RestController
@RequestMapping("/api/auth")
public class SocialSignupController {

    private final SocialLoginService socialLoginService;
    private final SignupService signupService;
    private final LoginService loginService;
    private final SessionLogin sessionLogin;
    private final SocialClientRegistrations registrations;

    public SocialSignupController(
            SocialLoginService socialLoginService,
            SignupService signupService,
            LoginService loginService,
            SessionLogin sessionLogin,
            SocialClientRegistrations registrations) {
        this.socialLoginService = socialLoginService;
        this.signupService = signupService;
        this.loginService = loginService;
        this.sessionLogin = sessionLogin;
        this.registrations = registrations;
    }

    /** 로그인 화면에 보일 소셜 버튼 (앱 키가 설정된 제공자만). */
    @GetMapping("/social-providers")
    public SocialProvidersResponse socialProviders() {
        return new SocialProvidersResponse(
                registrations.enabledProviders().stream().map(Enum::name).toList());
    }

    /** 마무리 화면의 미리 채운 값 ({@code getSocialSignupDraft}) → 200 / 410. */
    @GetMapping("/social-signup")
    public SocialSignupDraftResponse draft(HttpServletRequest request) {
        return SocialSignupDraftResponse.from(socialLoginService.draft(pending(request)));
    }

    /**
     * 가입 마무리 ({@code completeSocialSignup}) → 201. 계정을 만들고 세션 ID를 새로 발급해 로그인시킨다. 대기 정보는 지운다. 화면은
     * 응답의 {@code redirectTo}로 전체 페이지 이동한다(FR-032).
     */
    @PostMapping("/social-signup")
    @ResponseStatus(HttpStatus.CREATED)
    public SocialSignupResult complete(
            @RequestBody SocialSignupRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {
        if (body == null) {
            throw new ApiException(CommonReasonCode.MALFORMED_REQUEST);
        }
        HttpSession session = request.getSession(false);
        PendingSocialSignup pending = socialLoginService.requireFresh(pending(request));
        SocialSignupCommand command = body.toCommand();
        SignedUpMember member = signupService.completeSocialSignup(command, pending);
        String photo = command.useProfilePhoto() ? socialLoginService.photoUrl(pending) : null;

        LoginOutcome outcome = loginService.onSuccess(member.memberId());
        session.removeAttribute(SocialLoginSession.PENDING_SIGNUP);
        String redirectTo = SocialLoginSession.takeRedirect(session);
        sessionLogin.login(
                member.memberId(),
                member.role().name(),
                member.provider(),
                outcome,
                request,
                response);
        return new SocialSignupResult(
                member.handle(), member.nickname(), member.emailVerified(), photo, redirectTo);
    }

    /** 콜백에서 보관한 오류를 한 번 읽는다 ({@code popSocialLoginError}) → 200 오류 본문 / 204. */
    @GetMapping("/social-login-error")
    public ResponseEntity<String> popSocialLoginError(HttpServletRequest request) {
        String body = SocialLoginSession.popError(request.getSession(false));
        if (body == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body);
    }

    private static PendingSocialSignup pending(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        return session.getAttribute(SocialLoginSession.PENDING_SIGNUP)
                        instanceof PendingSocialSignup pending
                ? pending
                : null;
    }
}
