package com.team.blog.shared.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;
import java.util.Map;

/**
 * 공통 오류 본문 (02 §5-1 O8). 네 키가 항상 있다.
 *
 * <ul>
 *   <li>{@code errors}: 항상 배열. 칸 오류가 없으면 {@code []} (null 아님).
 *   <li>{@code details}: 추가 정보. 없으면 {@code null}.
 *   <li>{@code message}: 끝 마침표 없음.
 * </ul>
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@JsonPropertyOrder({"code", "message", "errors", "details"})
public record ErrorResponse(
        String code, String message, List<FieldError> errors, Map<String, Object> details) {

    public ErrorResponse {
        message = Messages.withoutTrailingPeriod(message);
        errors = errors == null ? List.of() : List.copyOf(errors);
        details = details == null || details.isEmpty() ? null : details;
    }

    public static ErrorResponse of(ReasonCode reason) {
        return new ErrorResponse(reason.code(), reason.defaultMessage(), List.of(), null);
    }

    public static ErrorResponse of(
            ReasonCode reason,
            String message,
            List<FieldError> errors,
            Map<String, Object> details) {
        return new ErrorResponse(
                reason.code(),
                message != null ? message : reason.defaultMessage(),
                errors,
                details);
    }

    /** 404 본문은 모든 경우 같다 (constitution III, README "정해진 것"). */
    public static ErrorResponse notFound() {
        return of(CommonReasonCode.NOT_FOUND);
    }
}
