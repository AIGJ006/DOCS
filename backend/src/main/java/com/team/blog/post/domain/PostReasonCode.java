package com.team.blog.post.domain;

import com.team.blog.shared.error.FieldError;
import com.team.blog.shared.error.ReasonCode;
import org.springframework.http.HttpStatus;

/**
 * post 모듈의 이유 코드 (001 {@code CommonReasonCode}는 고치지 않고 기능 enum으로 더한다 — 001 T018). 004·002가 함께 쓴다.
 *
 * <p>칸 오류({@code errors[].code})에만 쓰는 코드도 같은 enum에 두며, 그때 상태는 쓰이지 않는다(칸 오류를 모은 응답은 400 {@code
 * VALIDATION_FAILED}). 문구는 contracts/openapi.yaml 예시에서 끝 마침표를 뺀 것이다(README "정해진 것"). 렌더링 부하 제한
 * {@code CONTENT_TOO_COMPLEX}는 렌더러와 함께 {@code shared.application.markdown.MarkdownReasonCode}에 있다.
 */
public enum PostReasonCode implements ReasonCode {
    /** 등록되지 않은 공개 범위 값 (004 FR-020, openapi 400 예시). */
    INVALID_VISIBILITY(HttpStatus.BAD_REQUEST, "공개 범위를 다시 선택해 주세요"),

    // ---- 002 칸 오류 (발행 검증은 모아서 400 VALIDATION_FAILED, 저장 길이 초과는 한 칸) ----
    /** 발행 때 정리한 제목이 비었음 (05 §3). */
    TITLE_REQUIRED(HttpStatus.BAD_REQUEST, "제목을 입력해 주세요"),
    /** 제목 100자 초과 (저장·발행). */
    TITLE_TOO_LONG(HttpStatus.BAD_REQUEST, "제목은 100자까지예요"),
    /** 발행 때 본문이 공백뿐임. */
    CONTENT_REQUIRED(HttpStatus.BAD_REQUEST, "본문을 입력해 주세요"),
    /** 본문 100,000자 초과 (저장·발행·미리보기). */
    CONTENT_TOO_LONG(HttpStatus.BAD_REQUEST, "본문은 100,000자까지예요"),
    /** 본문에 업로드가 끝나지 않은 {@code local:} 사진이 있음 (발행만). */
    PENDING_IMAGES(HttpStatus.BAD_REQUEST, "업로드가 끝나지 않은 사진이 있어요"),
    /**
     * 태그 수 초과 (중복 제거 후 {@code blog.post.max-tags} 초과, field {@code tags}). 칸별 태그 거부 코드({@code
     * INVALID_TAG} 등)는 008 {@code tag.domain.TagReasonCode}에 있다.
     */
    TOO_MANY_TAGS(HttpStatus.BAD_REQUEST, "태그가 너무 많아요"),

    // ---- 002 응답 코드 ----
    /** 기준 버전이 현재 버전과 다름. {@code details.server = ServerCopy}. */
    VERSION_CONFLICT(HttpStatus.CONFLICT, "다른 탭이나 기기에서 이 글이 수정되었어요"),
    /** 같은 {@code Idempotency-Key}의 발행을 처리 중. 화면은 1초 뒤 같은 키로 다시. */
    IN_PROGRESS(HttpStatus.CONFLICT, "발행 중이에요"),
    /** 같은 {@code Idempotency-Key}로 다른 내용을 보냄. */
    IDEMPOTENCY_KEY_REUSED(HttpStatus.UNPROCESSABLE_CONTENT, "같은 요청 키로 다른 내용을 보낼 수 없어요"),
    /** {@code Idempotency-Key} 헤더 없음 (research B-8 제안). */
    IDEMPOTENCY_KEY_REQUIRED(HttpStatus.BAD_REQUEST, "요청 키가 필요해요"),
    /** {@code Idempotency-Key}가 UUID 형식이 아님 (research B-8 제안). */
    INVALID_IDEMPOTENCY_KEY(HttpStatus.BAD_REQUEST, "요청 키 형식이 올바르지 않아요"),
    /** 발행하지 않은 글의 변경 취소. */
    NOT_PUBLISHED(HttpStatus.CONFLICT, "발행한 글만 변경을 취소할 수 있어요"),
    /** 자동 저장 요청 본문 1MB 초과 (research B-2 제안). */
    PAYLOAD_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "요청이 너무 커요"),
    /** Redis 메모리 부족으로 자동 저장을 받지 못함 (FR-018: 밀어내지 않음, 브라우저가 재시도). */
    AUTOSAVE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "잠시 후 다시 저장할게요"),

    // ---- 006 내 글 관리 ----
    /** 관리 목록의 모르는 {@code tab} 값 (006 research R20 제안). {@code errors[0].field = "tab"}. */
    INVALID_TAB(HttpStatus.BAD_REQUEST, "목록 탭을 다시 선택해 주세요");

    private final HttpStatus status;
    private final String defaultMessage;

    PostReasonCode(HttpStatus status, String defaultMessage) {
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

    /** 이 코드의 칸 오류 한 건 ({@code errors[]} 항목). */
    public FieldError fieldError(String field) {
        return new FieldError(field, code(), defaultMessage);
    }
}
