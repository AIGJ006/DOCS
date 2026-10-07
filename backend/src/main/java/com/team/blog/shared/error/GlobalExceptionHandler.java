package com.team.blog.shared.error;

import com.team.blog.shared.web.CacheControlPolicy;
import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.BindException;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 모든 예외를 공통 오류 본문 {@code {code, message, errors, details}}로 바꾼다 (02 §5-1 O8).
 *
 * <ul>
 *   <li>{@link ApiException} 계열 → 이유 코드의 상태·코드, 예외의 칸 오류·추가 정보·헤더.
 *   <li>{@link NotFoundException}·없는 경로 → 항상 같은 404 본문.
 *   <li>그 밖의 예외 → 500 {@code INTERNAL_ERROR}. 예외 메시지·스택 트레이스는 응답에 넣지 않고 서버 로그에만 남긴다.
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException ex) {
        if (ex.status().is5xxServerError()) {
            log.warn("{}: {}", ex.reasonCode().code(), ex.getMessage(), ex.getCause());
        } else {
            log.debug("{}: {}", ex.reasonCode().code(), ex.getMessage());
        }
        HttpHeaders headers = new HttpHeaders();
        ex.headers().forEach(headers::set);
        if (ex instanceof NotFoundException) {
            // 모든 NotFoundException 하위 타입이 같은 본문·같은 헤더 (004 T019, research R-26·R-30)
            headers.setCacheControl(CacheControlPolicy.notFound());
        }
        return ResponseEntity.status(ex.status()).headers(headers).body(ex.toResponse());
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ErrorResponse> handleNoResource(Exception ex) {
        log.debug("404: {}", ex.getMessage());
        return respond(CommonReasonCode.NOT_FOUND, ErrorResponse.notFound());
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleInvalidBody(
            org.springframework.web.bind.MethodArgumentNotValidException ex) {
        return validationFailed(fieldErrors(ex.getBindingResult().getAllErrors()));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorResponse> handleBind(BindException ex) {
        return validationFailed(fieldErrors(ex.getBindingResult().getAllErrors()));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleMethodValidation(
            HandlerMethodValidationException ex) {
        List<FieldError> errors = new ArrayList<>();
        ex.getParameterValidationResults()
                .forEach(
                        result -> {
                            String name = result.getMethodParameter().getParameterName();
                            result.getResolvableErrors()
                                    .forEach(
                                            error ->
                                                    errors.add(
                                                            new FieldError(
                                                                    name,
                                                                    constraintCode(
                                                                            error.getCodes()),
                                                                    defaultText(
                                                                            error
                                                                                    .getDefaultMessage()))));
                        });
        return validationFailed(errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex) {
        List<FieldError> errors =
                ex.getConstraintViolations().stream()
                        .map(
                                v -> {
                                    String path = v.getPropertyPath().toString();
                                    String field = path.substring(path.lastIndexOf('.') + 1);
                                    String annotation =
                                            v.getConstraintDescriptor()
                                                    .getAnnotation()
                                                    .annotationType()
                                                    .getSimpleName();
                                    return new FieldError(
                                            field,
                                            toUpperSnake(annotation),
                                            defaultText(v.getMessage()));
                                })
                        .toList();
        return validationFailed(errors);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException ex) {
        return validationFailed(
                List.of(new FieldError(ex.getParameterName(), "REQUIRED", "필수 값이에요")));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex) {
        return validationFailed(
                List.of(new FieldError(ex.getName(), "INVALID_FORMAT", "형식이 올바르지 않아요")));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        log.debug("읽을 수 없는 요청 본문: {}", ex.getMessage());
        return respond(CommonReasonCode.MALFORMED_REQUEST);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex) {
        return respond(CommonReasonCode.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaType(HttpMediaTypeNotSupportedException ex) {
        return respond(CommonReasonCode.UNSUPPORTED_MEDIA_TYPE);
    }

    /** Service의 메서드 보안 등에서 올라온 인증 예외 → 401. */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex) {
        return respond(CommonReasonCode.LOGIN_REQUIRED);
    }

    /** 권한 없음: 비로그인이면 401, 로그인했으면 볼 수 없는 것과 같게 404 (constitution III). */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || !auth.isAuthenticated()
                || auth instanceof AnonymousAuthenticationToken) {
            return respond(CommonReasonCode.LOGIN_REQUIRED);
        }
        return respond(CommonReasonCode.NOT_FOUND, ErrorResponse.notFound());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException ex) {
        HttpStatusCode status = ex.getStatusCode();
        if (status.value() == 404) {
            return respond(CommonReasonCode.NOT_FOUND, ErrorResponse.notFound());
        }
        if (status.is4xxClientError()) {
            return ResponseEntity.status(status)
                    .body(ErrorResponse.of(CommonReasonCode.MALFORMED_REQUEST));
        }
        return handleUnexpected(ex);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("처리하지 못한 예외", ex);
        return respond(CommonReasonCode.INTERNAL_ERROR);
    }

    private static ResponseEntity<ErrorResponse> respond(CommonReasonCode reason) {
        return respond(reason, ErrorResponse.of(reason));
    }

    private static ResponseEntity<ErrorResponse> respond(
            CommonReasonCode reason, ErrorResponse body) {
        if (reason == CommonReasonCode.NOT_FOUND) {
            return ResponseEntity.status(reason.status())
                    .header(HttpHeaders.CACHE_CONTROL, CacheControlPolicy.notFound())
                    .body(body);
        }
        return ResponseEntity.status(reason.status()).body(body);
    }

    private static ResponseEntity<ErrorResponse> validationFailed(List<FieldError> errors) {
        return ResponseEntity.status(CommonReasonCode.VALIDATION_FAILED.status())
                .body(ErrorResponse.of(CommonReasonCode.VALIDATION_FAILED, null, errors, null));
    }

    private static List<FieldError> fieldErrors(List<ObjectError> objectErrors) {
        return objectErrors.stream()
                .map(
                        error -> {
                            String field =
                                    error instanceof org.springframework.validation.FieldError fe
                                            ? fe.getField()
                                            : error.getObjectName();
                            return new FieldError(
                                    field,
                                    toUpperSnake(error.getCode()),
                                    defaultText(error.getDefaultMessage()));
                        })
                .toList();
    }

    private static String constraintCode(String[] codes) {
        if (codes == null || codes.length == 0) {
            return "INVALID";
        }
        // codes는 "NotBlank.object.field", ..., "NotBlank" 순서. 마지막이 제약 이름이다.
        return toUpperSnake(codes[codes.length - 1]);
    }

    /** {@code NotBlank} → {@code NOT_BLANK}. */
    static String toUpperSnake(String name) {
        if (name == null || name.isBlank()) {
            return "INVALID";
        }
        return name.replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .replace('.', '_')
                .toUpperCase(Locale.ROOT);
    }

    private static String defaultText(String message) {
        return message == null || message.isBlank() ? "올바르지 않은 값이에요" : message;
    }
}
