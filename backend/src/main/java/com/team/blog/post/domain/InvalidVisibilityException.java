package com.team.blog.post.domain;

import com.team.blog.shared.error.BusinessRuleException;
import com.team.blog.shared.error.FieldError;
import java.util.List;

/**
 * 허용 집합 밖의 공개 범위 → 400 {@code INVALID_VISIBILITY} (FR-020, research R-21). 판정 순서의 ⑤ 업무 규칙 단계라 남의
 * 글이면 404가 먼저다.
 *
 * <pre>{@code
 * {code: INVALID_VISIBILITY, message: "공개 범위를 다시 선택해 주세요",
 *  errors: [{field: <넘긴 칸 이름>, code: INVALID_VISIBILITY, message: "허용되지 않은 공개 범위예요"}], details: null}
 * }</pre>
 */
public class InvalidVisibilityException extends BusinessRuleException {

    static final String FIELD_MESSAGE = "허용되지 않은 공개 범위예요";

    private final String field;

    public InvalidVisibilityException(String field) {
        super(
                PostReasonCode.INVALID_VISIBILITY,
                PostReasonCode.INVALID_VISIBILITY.defaultMessage(),
                List.of(
                        new FieldError(
                                field, PostReasonCode.INVALID_VISIBILITY.code(), FIELD_MESSAGE)),
                null);
        this.field = field;
    }

    /** 오류가 난 칸 이름 (예: {@code visibility}, {@code defaultVisibility}). */
    public String field() {
        return field;
    }

    /** 다른 칸 오류와 함께 모아 보낼 때(002 발행 검증) 쓰는 칸 오류 한 건. */
    public static FieldError fieldError(String field) {
        return new FieldError(field, PostReasonCode.INVALID_VISIBILITY.code(), FIELD_MESSAGE);
    }
}
