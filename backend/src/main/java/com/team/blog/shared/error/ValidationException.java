package com.team.blog.shared.error;

import java.util.List;
import java.util.Map;

/** 입력 검증 실패 → 400 {@code VALIDATION_FAILED} + 칸별 오류 {@code errors[]} (한 요청의 칸 오류는 모아서 한 번에). */
public class ValidationException extends ApiException {

    public ValidationException(List<FieldError> errors) {
        this(errors, null);
    }

    public ValidationException(List<FieldError> errors, Map<String, Object> details) {
        super(
                CommonReasonCode.VALIDATION_FAILED,
                CommonReasonCode.VALIDATION_FAILED.defaultMessage(),
                errors,
                details,
                Map.of());
    }

    /** 칸 오류 하나. */
    public static ValidationException of(String field, String code, String message) {
        return new ValidationException(List.of(new FieldError(field, code, message)));
    }
}
