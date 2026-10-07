package com.team.blog.shared.error;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * 공통 오류 본문으로 바뀌는 업무 예외의 기반. {@link GlobalExceptionHandler}가 {@link #reasonCode()}의 상태·코드와 {@link
 * #getMessage()}(사용자 문구), {@link #errors()}, {@link #details()}, {@link #headers()}로 응답을 만든다.
 */
public class ApiException extends RuntimeException {

    private final ReasonCode reasonCode;
    private final List<FieldError> errors;
    private final Map<String, Object> details;
    private final Map<String, String> headers;

    public ApiException(ReasonCode reasonCode) {
        this(reasonCode, reasonCode.defaultMessage(), List.of(), null, Map.of());
    }

    public ApiException(ReasonCode reasonCode, String message) {
        this(reasonCode, message, List.of(), null, Map.of());
    }

    public ApiException(ReasonCode reasonCode, Map<String, Object> details) {
        this(reasonCode, reasonCode.defaultMessage(), List.of(), details, Map.of());
    }

    public ApiException(
            ReasonCode reasonCode,
            String message,
            List<FieldError> errors,
            Map<String, Object> details,
            Map<String, String> headers) {
        super(message != null ? message : reasonCode.defaultMessage());
        this.reasonCode = reasonCode;
        this.errors = errors == null ? List.of() : List.copyOf(errors);
        this.details = details == null ? null : new LinkedHashMap<>(details);
        this.headers = headers == null ? Map.of() : Map.copyOf(headers);
    }

    public ReasonCode reasonCode() {
        return reasonCode;
    }

    public HttpStatus status() {
        return reasonCode.status();
    }

    public List<FieldError> errors() {
        return errors;
    }

    public Map<String, Object> details() {
        return details;
    }

    /** 응답에 함께 실을 헤더 (예: {@code Retry-After}). */
    public Map<String, String> headers() {
        return headers;
    }

    /** 응답 본문. */
    public ErrorResponse toResponse() {
        return ErrorResponse.of(reasonCode, getMessage(), errors, details);
    }
}
