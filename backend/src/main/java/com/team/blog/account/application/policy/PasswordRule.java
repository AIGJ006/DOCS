package com.team.blog.account.application.policy;

import com.team.blog.account.application.AccountReasonCode;

/**
 * 비밀번호 규칙 (FR-013, R-14). 앞의 다섯 개는 화면 체크리스트({@code PasswordRuleChecklist})와 같은 순서·같은 규칙이다(FR-014).
 * 나머지는 서버만 판단한다(이메일 포함·흔한 목록·확인 일치).
 */
public enum PasswordRule {
    /** 8~16자 (최대 16자). */
    LENGTH(AccountReasonCode.PASSWORD_INVALID_LENGTH, true),
    /** 영문 대문자·소문자 각 1개 이상. */
    LETTER_CASE(AccountReasonCode.PASSWORD_MISSING_CHAR_TYPE, true),
    /** 숫자 1개 이상. */
    DIGIT(AccountReasonCode.PASSWORD_MISSING_CHAR_TYPE, true),
    /** 지정 특수문자 1개 이상. */
    SPECIAL(AccountReasonCode.PASSWORD_MISSING_CHAR_TYPE, true),
    /** 영문·숫자·지정 특수문자만 (공백·한글 불가). */
    ALLOWED_CHARS(AccountReasonCode.PASSWORD_INVALID_CHAR, true),
    /** 이메일 {@code @} 앞부분({@code +} 앞, 3자 이상일 때) 미포함. */
    NOT_EMAIL(AccountReasonCode.PASSWORD_CONTAINS_EMAIL, false),
    /** 흔한 비밀번호 목록과 완전히 같지 않음(대소문자 무시). */
    NOT_COMMON(AccountReasonCode.PASSWORD_TOO_COMMON, false),
    /** 비밀번호 확인과 같음. */
    CONFIRM(AccountReasonCode.PASSWORD_CONFIRM_MISMATCH, false);

    private final AccountReasonCode failureCode;
    private final boolean checklist;

    PasswordRule(AccountReasonCode failureCode, boolean checklist) {
        this.failureCode = failureCode;
        this.checklist = checklist;
    }

    public AccountReasonCode failureCode() {
        return failureCode;
    }

    /** 화면 체크리스트에 보이는 규칙인가. */
    public boolean checklist() {
        return checklist;
    }
}
