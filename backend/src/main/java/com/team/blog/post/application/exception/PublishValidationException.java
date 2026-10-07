package com.team.blog.post.application.exception;

import com.team.blog.shared.error.FieldError;
import com.team.blog.shared.error.ValidationException;
import java.util.List;

/**
 * 발행 검증 실패 → 400 {@code VALIDATION_FAILED}, 항목별 칸 오류를 모두 {@code errors[]}에 (data-model §3: 필드
 * {@code title}, {@code contentMd}, {@code tags[i]}, {@code visibility}).
 */
public class PublishValidationException extends ValidationException {

    public PublishValidationException(List<FieldError> errors) {
        super(errors);
        if (errors.isEmpty()) {
            throw new IllegalArgumentException("칸 오류가 하나 이상 있어야 합니다");
        }
    }
}
