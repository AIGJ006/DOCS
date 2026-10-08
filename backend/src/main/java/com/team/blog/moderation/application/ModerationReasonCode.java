package com.team.blog.moderation.application;

import com.team.blog.shared.error.ReasonCode;
import org.springframework.http.HttpStatus;

/**
 * 신고·숨김 이유 코드 (014 data-model §5). 원문(43 §2)에 있는 것은 {@code CANNOT_REPORT_OWN}·{@code
 * REPORT_ALREADY_HANDLED}이고 나머지는 제안이다(팀 확인 T004). 정지 코드 3개는 account {@code AccountReasonCode}에 있다.
 *
 * <p>{@link #REPORT_DETAIL_REQUIRED}는 칸 오류({@code errors[].code}, {@code field: detail})로만 나간다 — 응답
 * 전체는 400 {@code VALIDATION_FAILED}다(001·002 칸 규칙 방식).
 */
public enum ModerationReasonCode implements ReasonCode {
    CANNOT_REPORT_OWN(HttpStatus.BAD_REQUEST, "내 글이나 댓글은 신고할 수 없어요"),
    REPORT_DETAIL_REQUIRED(HttpStatus.BAD_REQUEST, "기타 사유를 적어 주세요"),
    REPORT_ALREADY_HANDLED(HttpStatus.CONFLICT, "이미 처리된 신고예요"),
    CANNOT_MODERATE_OWN(HttpStatus.BAD_REQUEST, "내 글이나 댓글은 처리할 수 없어요"),
    CANNOT_HANDLE_OWN_REPORT(HttpStatus.BAD_REQUEST, "내가 혼자 신고한 건은 처리할 수 없어요");

    private final HttpStatus status;
    private final String defaultMessage;

    ModerationReasonCode(HttpStatus status, String defaultMessage) {
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
