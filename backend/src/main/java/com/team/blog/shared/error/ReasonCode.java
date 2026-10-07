package com.team.blog.shared.error;

import org.springframework.http.HttpStatus;

/**
 * 오류 이유 코드. 기능마다 enum으로 구현해 코드를 더한다(예: {@code PostReasonCode}). 공통 코드는 {@link CommonReasonCode}.
 *
 * <p>{@link #defaultMessage()}는 사용자에게 보이는 한국어 문구이며 끝에 마침표를 붙이지 않는다(2026-10-07 결정).
 */
public interface ReasonCode {

    /** 대문자 이유 코드 (응답의 {@code code}). */
    String code();

    /** HTTP 상태. */
    HttpStatus status();

    /** 기본 문구 (응답의 {@code message}). */
    String defaultMessage();
}
