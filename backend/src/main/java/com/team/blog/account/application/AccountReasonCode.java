package com.team.blog.account.application;

import com.team.blog.shared.error.ReasonCode;
import org.springframework.http.HttpStatus;

/**
 * 계정·인증 기능의 이유 코드 (contracts/openapi.yaml, 09 §3, 11 §3·§5·§6-2, 42 §4). 원문에 없는 코드는 "제안"이며 팀 확인이
 * 필요하다(R-32). 칸 오류({@code errors[].code})로 쓰이는 코드의 상태는 응답 전체의 상태가 아니다 — 응답은 400 {@code
 * VALIDATION_FAILED}이고 이 코드는 칸별 이유다.
 *
 * <p>문구는 끝에 마침표를 붙이지 않는다(2026-10-07 결정). 금칙어 문구는 어떤 단어에 걸렸는지 알려주지 않는다(N-5).
 */
public enum AccountReasonCode implements ReasonCode {

    // ---- 이메일 (07 §3, R-10)
    EMAIL_INVALID_FORMAT(HttpStatus.BAD_REQUEST, "이메일 형식이 올바르지 않아요"),
    EMAIL_ALREADY_REGISTERED(HttpStatus.BAD_REQUEST, "이미 가입된 이메일이에요. [로그인] [비밀번호 찾기]"),

    // ---- 블로그 주소 (08 §2·§4·§5, R-15)
    HANDLE_INVALID_FORMAT(
            HttpStatus.BAD_REQUEST, "영문 소문자·숫자·_로 3~36자까지 쓸 수 있어요 (처음과 끝은 영문 소문자나 숫자)"),
    HANDLE_PREFIX_MISMATCH(HttpStatus.BAD_REQUEST, "블로그 주소 접두어가 가입 수단과 맞지 않아요"),
    HANDLE_RESERVED(HttpStatus.BAD_REQUEST, "사용할 수 없는 주소예요"),
    HANDLE_BANNED_WORD(HttpStatus.BAD_REQUEST, "사용할 수 없는 단어가 들어 있어요"),
    HANDLE_DUPLICATE(HttpStatus.BAD_REQUEST, "이미 사용 중인 주소예요"),

    // ---- 닉네임 (09 §3 — 문구 원문 그대로)
    NICKNAME_INVALID_FORMAT(HttpStatus.BAD_REQUEST, "한글·영문·숫자로 2~10자까지 쓸 수 있어요 (공백·특수문자 불가)"),
    NICKNAME_LETTER_REQUIRED(HttpStatus.BAD_REQUEST, "한글이나 영문을 1자 이상 넣어 주세요"),
    NICKNAME_RESERVED(HttpStatus.BAD_REQUEST, "사용할 수 없는 닉네임이에요"),
    NICKNAME_BANNED_WORD(HttpStatus.BAD_REQUEST, "사용할 수 없는 단어가 들어 있어요"),
    NICKNAME_DUPLICATE(HttpStatus.BAD_REQUEST, "이미 사용 중인 닉네임이에요"),
    NICKNAME_CHANGE_TOO_SOON(HttpStatus.CONFLICT, "닉네임은 바꾼 뒤 30일이 지나야 다시 바꿀 수 있어요"),

    // ---- 비밀번호 (07 §4, FR-013, R-14)
    PASSWORD_INVALID_LENGTH(HttpStatus.BAD_REQUEST, "비밀번호는 8~16자로 입력해 주세요 (최대 16자)"),
    PASSWORD_MISSING_CHAR_TYPE(
            HttpStatus.BAD_REQUEST, "영문 대문자·소문자·숫자·특수문자를 각각 1개 이상 넣어 주세요 (8~16자, 최대 16자)"),
    PASSWORD_INVALID_CHAR(HttpStatus.BAD_REQUEST, "영문·숫자·지정 특수문자만 쓸 수 있어요 (공백·한글 불가)"),
    PASSWORD_CONTAINS_EMAIL(HttpStatus.BAD_REQUEST, "이메일 앞부분이 들어간 비밀번호는 쓸 수 없어요"),
    PASSWORD_TOO_COMMON(HttpStatus.BAD_REQUEST, "너무 흔한 비밀번호예요. 다른 비밀번호를 써 주세요"),
    PASSWORD_CONFIRM_MISMATCH(HttpStatus.BAD_REQUEST, "비밀번호 확인이 일치하지 않아요"),
    PASSWORD_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "소셜 로그인 계정은 비밀번호가 없어요"),
    PASSWORD_SAME_AS_CURRENT(HttpStatus.BAD_REQUEST, "지금 비밀번호와 다른 비밀번호를 입력해 주세요"),
    CURRENT_PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "현재 비밀번호가 올바르지 않아요"),
    PASSWORD_CHANGE_TEMPORARILY_LOCKED(HttpStatus.TOO_MANY_REQUESTS, "잠시 후 다시 시도해 주세요(약 15분)"),

    // ---- 약관 동의 (07 §3-1, FR-010·012)
    AGREEMENT_REQUIRED(HttpStatus.BAD_REQUEST, "이용약관과 개인정보 처리방침에 동의해 주세요"),
    AGREEMENT_VERSION_MISMATCH(HttpStatus.BAD_REQUEST, "약관이 바뀌었어요. 새로 고친 뒤 다시 동의해 주세요"),
    REAGREEMENT_REQUIRED(HttpStatus.FORBIDDEN, "바뀐 약관에 동의한 뒤 이용할 수 있어요"),

    // ---- 인증·로그인 (07 §3·§6, R-11·R-12)
    LINK_EXPIRED(HttpStatus.BAD_REQUEST, "링크가 만료됐어요. [인증 메일 다시 보내기]"),
    ALREADY_VERIFIED(HttpStatus.CONFLICT, "이미 인증된 계정이에요"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않아요"),
    LOGIN_TEMPORARILY_LOCKED(HttpStatus.TOO_MANY_REQUESTS, "잠시 후 다시 시도해 주세요(약 15분)"),
    SOCIAL_SIGNUP_EXPIRED(HttpStatus.GONE, "소셜 로그인 정보가 만료됐어요. 다시 소셜 로그인해 주세요"),
    /** 소셜 콜백 실패: state 불일치·제공자 오류 (제안). {@code GET /api/auth/social-login-error} 본문으로만 나간다. */
    SOCIAL_LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "소셜 로그인을 마치지 못했어요. 다시 시도해 주세요"),
    /** Google 이메일 미확인({@code email_verified = false}) — Google은 확인된 이메일만 받는다 (FR-008, 제안). */
    SOCIAL_EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN, "이메일 확인을 마친 Google 계정으로만 가입할 수 있어요"),

    // ---- 프로필 (11 §3·§4)
    BIO_TOO_LONG(HttpStatus.BAD_REQUEST, "소개는 200자까지 쓸 수 있어요"),
    BIO_TOO_MANY_LINES(HttpStatus.BAD_REQUEST, "소개는 4줄까지 쓸 수 있어요"),
    BIO_BANNED_WORD(HttpStatus.BAD_REQUEST, "사용할 수 없는 단어가 들어 있어요"),
    INVALID_PROFILE_IMAGE(HttpStatus.BAD_REQUEST, "사용할 수 없는 사진이에요"),

    // ---- 친구 (06 §6-2, R-27)
    CANNOT_FRIEND_SELF(HttpStatus.BAD_REQUEST, "자기 자신에게는 친구 요청을 보낼 수 없어요"),

    // ---- 회원 탈퇴·복구 (015 data-model §4). 잠금은 비밀번호 변경과 같은 PASSWORD_CHANGE_TEMPORARILY_LOCKED
    /** 관리자 권한이 있는 회원의 탈퇴 신청 (W-6). */
    ADMIN_CANNOT_WITHDRAW(HttpStatus.CONFLICT, "관리자 권한을 해제한 뒤 탈퇴할 수 있어요"),
    /** 안내 확인 체크({@code confirmed})가 없는 신청 (FR-002, 제안 코드·문구). */
    WITHDRAW_CONFIRM_REQUIRED(HttpStatus.BAD_REQUEST, "안내 내용을 확인하고 체크해 주세요"),
    /** 소셜 가입 회원의 확인 문구가 "탈퇴"와 다름 (제안 문구). */
    CONFIRM_TEXT_MISMATCH(HttpStatus.BAD_REQUEST, "'탈퇴'를 정확히 입력해 주세요"),
    /** 복구 기한(신청 + 30일)이 지난 복구 요청 (FR-021a). */
    RESTORE_PERIOD_EXPIRED(HttpStatus.CONFLICT, "복구 기한이 지났어요"),
    /** 가입 email 칸 오류: 같은 이메일의 이메일 가입 계정이 탈퇴 유예 중 (FR-022). 응답은 400 VALIDATION_FAILED. */
    EMAIL_WITHDRAWAL_PENDING(HttpStatus.BAD_REQUEST, "탈퇴 신청한 계정이 있어요. 로그인하면 복구할 수 있어요");

    private final HttpStatus status;
    private final String defaultMessage;

    AccountReasonCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }
}
