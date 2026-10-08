package com.team.blog.media.domain;

import com.team.blog.shared.error.FieldError;
import com.team.blog.shared.error.ReasonCode;
import org.springframework.http.HttpStatus;

/**
 * media 모듈의 이유 코드 (data-model §7). 문구 끝에 마침표를 붙이지 않는다(README 2026-10-07). 1분 제한은 공통 {@code
 * CommonReasonCode.TOO_MANY_REQUESTS}를 쓴다(007 Q3).
 *
 * <p>칸 오류({@code errors[].code})에만 쓰는 코드는 상태가 쓰이지 않는다(칸 오류를 모은 응답은 400 {@code VALIDATION_FAILED}).
 */
public enum MediaReasonCode implements ReasonCode {
    // ---- presign 칸 오류 ----
    /** {@code contentType}·{@code thumbContentType}이 받는 형식이 아님. */
    UNSUPPORTED_IMAGE_TYPE(HttpStatus.BAD_REQUEST, "jpg, png, gif, webp 사진만 올릴 수 있어요"),
    /** {@code size}가 한도(10MB) 밖. */
    IMAGE_TOO_LARGE(HttpStatus.BAD_REQUEST, "사진은 10MB까지 올릴 수 있어요"),
    /** {@code thumbSize}가 한도(1MB) 밖. */
    THUMBNAIL_TOO_LARGE(HttpStatus.BAD_REQUEST, "사진을 처리하지 못했어요"),
    /** 글 사진인데 썸네일 칸이 없음. */
    THUMBNAIL_REQUIRED(HttpStatus.BAD_REQUEST, "사진을 처리하지 못했어요"),
    /** 프로필 사진인데 썸네일 칸이 있음. */
    THUMBNAIL_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "사진을 처리하지 못했어요"),

    // ---- 응답 코드 ----
    /** 1인 저장 공간 초과. {@code details: {usedBytes, quotaBytes}}. */
    STORAGE_QUOTA_EXCEEDED(
            HttpStatus.CONFLICT, "사진 저장 공간(1GB)을 다 썼어요. 쓰지 않는 사진이 든 글을 지우면 7일 뒤 공간이 돌아와요"),
    /** 하루 장수 초과. {@code Retry-After}(다음 0시 KST까지). */
    DAILY_UPLOAD_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "오늘은 사진을 200장까지 올릴 수 있어요. 내일 다시 시도해 주세요"),
    /** complete 때 파일이 없음(만료 포함). */
    IMAGE_NOT_UPLOADED(HttpStatus.BAD_REQUEST, "사진이 올라가지 않았어요. 다시 시도해 주세요"),
    /** complete 검사 실패. {@code details.reason} = {@link ImageRejectReason}. */
    IMAGE_REJECTED(HttpStatus.BAD_REQUEST, "올릴 수 없는 사진이에요");

    private final HttpStatus status;
    private final String defaultMessage;

    MediaReasonCode(HttpStatus status, String defaultMessage) {
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

    /** 이 코드의 칸 오류. */
    public FieldError fieldError(String field) {
        return new FieldError(field, name(), defaultMessage);
    }
}
