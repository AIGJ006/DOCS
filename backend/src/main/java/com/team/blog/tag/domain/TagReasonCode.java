package com.team.blog.tag.domain;

import com.team.blog.shared.error.FieldError;
import com.team.blog.shared.error.ReasonCode;
import org.springframework.http.HttpStatus;

/**
 * 태그 거부 이유 (008 data-model §2-2, research R3). 발행 검증의 칸 오류({@code errors[].field = "tags[i]"})로만 쓴다
 * — 칸 오류를 모은 응답은 400 {@code VALIDATION_FAILED}다. 002 {@code PostReasonCode.INVALID_TAG}를 여기로 옮겼고 응답
 * {@code code} 문자열은 같다. 개수 초과 {@code TOO_MANY_TAGS}는 발행 설정 규칙이라 {@code PostReasonCode}에 남는다.
 *
 * <p>문구 끝에 마침표를 붙이지 않는다(README "정해진 것"). 금칙어 거부 문구에는 어떤 단어인지 넣지 않는다.
 */
public enum TagReasonCode implements ReasonCode {
    /** 허용 밖 문자, 한글·영문·숫자 없음, 정리 결과가 빈 값. */
    INVALID_TAG("쓸 수 없는 글자가 있어요"),
    /** 정리 후 코드 포인트 30 초과. */
    TAG_TOO_LONG("태그는 30자까지 쓸 수 있어요"),
    /** 001 {@code BannedWordFilter}에 걸림. */
    TAG_BANNED_WORD("쓸 수 없는 단어가 들어 있어요");

    private final String defaultMessage;

    TagReasonCode(String defaultMessage) {
        this.defaultMessage = defaultMessage;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.BAD_REQUEST;
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }

    /** 이 코드의 칸 오류 한 건 ({@code errors[]} 항목, 예: {@code tags[2]}). */
    public FieldError fieldError(String field) {
        return new FieldError(field, code(), defaultMessage);
    }
}
