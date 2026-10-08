package com.team.blog.account.infra.security;

import jakarta.servlet.http.HttpSession;

/**
 * 소셜 로그인 세션 속성 (R-07·R-33, openapi {@code socialLoginCallback}).
 *
 * <ul>
 *   <li>{@link #PENDING_SIGNUP}: 가입 대기 정보({@code PendingSocialSignup}, 10분). 저장하지 못하면 거부해야 하는
 *       속성이다({@code ResilientSessionRepository}).
 *   <li>{@link #LOGIN_REDIRECT}: 시작할 때 받은 {@code redirect}(검사 통과 값). 로그인·가입 마무리 뒤 이동한다.
 *   <li>{@link #LOGIN_ERROR}: 콜백에서 생긴 오류 본문(JSON 문자열). {@code GET /api/auth/social-login-error}가 한
 *       번 읽고 지운다.
 * </ul>
 */
public final class SocialLoginSession {

    public static final String PENDING_SIGNUP = "pendingSocialSignup";
    public static final String LOGIN_REDIRECT = "loginRedirect";
    public static final String LOGIN_ERROR = "socialLoginError";

    private SocialLoginSession() {}

    /** 보관한 이동 주소를 꺼내고 지운다. 없으면 {@code /}. */
    public static String takeRedirect(HttpSession session) {
        if (session == null) {
            return SafeRedirectResolver.FALLBACK;
        }
        Object value = session.getAttribute(LOGIN_REDIRECT);
        session.removeAttribute(LOGIN_REDIRECT);
        return value instanceof String s ? s : SafeRedirectResolver.FALLBACK;
    }

    /** 보관한 오류 본문을 꺼내고 지운다. 없으면 null. */
    public static String popError(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute(LOGIN_ERROR);
        if (value != null) {
            session.removeAttribute(LOGIN_ERROR);
        }
        return value instanceof String s ? s : null;
    }
}
