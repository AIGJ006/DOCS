package com.team.blog.shared.web.cursor;

import com.team.blog.shared.error.ApiException;
import com.team.blog.shared.error.CommonReasonCode;
import com.team.blog.shared.error.ErrorResponse;

/** 풀리지 않거나 다른 목록의 커서 → 400 {@code INVALID_CURSOR}. 생성자 설명은 서버 로그용이다. */
public class InvalidCursorException extends ApiException {

    public InvalidCursorException(String logReason) {
        super(CommonReasonCode.INVALID_CURSOR, logReason);
    }

    @Override
    public ErrorResponse toResponse() {
        return ErrorResponse.of(CommonReasonCode.INVALID_CURSOR);
    }
}
