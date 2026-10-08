package com.team.blog.category.domain;

import com.team.blog.shared.error.FieldError;
import com.team.blog.shared.error.ReasonCode;
import org.springframework.http.HttpStatus;

/**
 * 카테고리 이유 코드 (017 contracts). 문구 끝에 마침표를 붙이지 않는다(README "정해진 것"). 이름 칸 오류는 400 {@code
 * VALIDATION_FAILED}의 {@code errors[]}로만 쓴다.
 */
public enum CategoryReasonCode implements ReasonCode {
    /** 정리한 이름이 비었음 (칸 {@code name}). */
    CATEGORY_NAME_REQUIRED(HttpStatus.BAD_REQUEST, "카테고리 이름을 입력해 주세요"),
    /** 정리한 이름이 30자 초과 (칸 {@code name}). */
    CATEGORY_NAME_TOO_LONG(HttpStatus.BAD_REQUEST, "카테고리 이름은 30자까지예요"),
    /** 같은 상위 안에 같은 이름(대소문자 무시)이 있음. */
    CATEGORY_NAME_DUPLICATED(HttpStatus.CONFLICT, "같은 이름의 카테고리가 이미 있어요"),
    /** 하위 아래에 만들기, 하위가 있는 카테고리를 다른 카테고리 아래로 옮기기, 자기 자신을 상위로 고르기. */
    CATEGORY_DEPTH_EXCEEDED(HttpStatus.BAD_REQUEST, "카테고리는 2단계까지 만들 수 있어요"),
    /** 회원당 최대 개수({@code blog.category.max-count}) 초과. */
    TOO_MANY_CATEGORIES(HttpStatus.BAD_REQUEST, "카테고리를 더 만들 수 없어요"),
    /** 하위가 있는 카테고리 삭제. */
    CATEGORY_HAS_CHILDREN(HttpStatus.CONFLICT, "하위 카테고리를 먼저 옮기거나 지워 주세요"),
    /** 순서 저장 요청의 번호 묶음이 지금 그 상위의 하위 묶음과 다름. */
    CATEGORY_ORDER_STALE(HttpStatus.CONFLICT, "카테고리 목록이 바뀌었어요. 새로 불러올게요"),
    /** 내 카테고리가 아니거나 없는 번호 (상위 지정·글 지정). */
    INVALID_CATEGORY(HttpStatus.BAD_REQUEST, "카테고리를 다시 선택해 주세요");

    private final HttpStatus status;
    private final String defaultMessage;

    CategoryReasonCode(HttpStatus status, String defaultMessage) {
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

    /** 이 코드의 칸 오류 한 건. */
    public FieldError fieldError(String field) {
        return new FieldError(field, code(), defaultMessage);
    }
}
