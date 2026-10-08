package com.team.blog.post.domain;

import com.team.blog.shared.error.BusinessRuleException;
import com.team.blog.shared.error.FieldError;
import java.util.List;

/**
 * 관리 목록의 모르는 {@code tab} 값 → 400 {@code INVALID_TAB} (006 T038, research R20 제안). 조용히 임시글 탭으로 보이면
 * 클라이언트 버그를 숨기게 되어 거부한다.
 *
 * <pre>{@code
 * {code: INVALID_TAB, message: "목록 탭을 다시 선택해 주세요",
 *  errors: [{field: "tab", code: INVALID_TAB, message: "없는 목록 탭이에요"}], details: null}
 * }</pre>
 */
public class InvalidTabException extends BusinessRuleException {

    static final String FIELD = "tab";
    static final String FIELD_MESSAGE = "없는 목록 탭이에요";

    public InvalidTabException() {
        super(
                PostReasonCode.INVALID_TAB,
                PostReasonCode.INVALID_TAB.defaultMessage(),
                List.of(new FieldError(FIELD, PostReasonCode.INVALID_TAB.code(), FIELD_MESSAGE)),
                null);
    }
}
