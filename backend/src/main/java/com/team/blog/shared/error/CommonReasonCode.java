package com.team.blog.shared.error;

import org.springframework.http.HttpStatus;

/** 모든 기능이 함께 쓰는 이유 코드 (02 §5-1, 42 §3·§4). 원문에 없는 코드는 "제안" 표시. */
public enum CommonReasonCode implements ReasonCode {
    /** 입력 검증 실패. {@code errors[]}에 칸별 오류. */
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "입력한 내용을 확인해 주세요"),
    /** 읽을 수 없는 요청 본문 (제안). */
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않아요"),
    /** 없거나 볼 수 없음. 모든 경우 같은 본문 (constitution III). */
    NOT_FOUND(HttpStatus.NOT_FOUND, "볼 수 없는 페이지예요"),
    /** 비로그인 (세션을 읽지 못한 Redis 장애 포함). */
    LOGIN_REQUIRED(HttpStatus.UNAUTHORIZED, "로그인이 필요해요"),
    /** CSRF 토큰 없음·불일치 (제안). */
    CSRF_REJECTED(HttpStatus.FORBIDDEN, "요청을 확인하지 못했어요. 페이지를 새로 고친 뒤 다시 시도해 주세요"),
    /** 이메일 인증 전 콘텐츠 쓰기 (42 P-6). */
    EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN, "이메일 인증 후 이용할 수 있어요"),
    /** 정지 계정 (H7). */
    ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN, "정지된 계정이에요"),
    /** 탈퇴 유예 계정 (42 P-12). */
    ACCOUNT_WITHDRAWN(HttpStatus.FORBIDDEN, "탈퇴 신청한 계정이에요"),
    /** 잘못된 목록 커서. */
    INVALID_CURSOR(HttpStatus.BAD_REQUEST, "목록을 처음부터 다시 불러와 주세요"),
    /** 요청 제한 초과. {@code Retry-After} 헤더. */
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "잠시 후 다시 시도해 주세요"),
    /** Redis 장애로 보안 확인을 할 수 없음 (제안, R-30). {@code Retry-After: 30}. */
    TEMPORARILY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "잠시 후 다시 시도해 주세요"),
    /** 지원하지 않는 HTTP 메서드 (제안). */
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청이에요"),
    /** 지원하지 않는 본문 형식 (제안). */
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 요청 형식이에요"),
    /** 서버 오류. 내부 정보는 응답에 넣지 않는다. */
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했어요");

    private final HttpStatus status;
    private final String defaultMessage;

    CommonReasonCode(HttpStatus status, String defaultMessage) {
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
}
