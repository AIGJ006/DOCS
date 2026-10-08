package com.team.blog.interaction.domain;

import com.team.blog.shared.error.FieldError;
import com.team.blog.shared.error.ReasonCode;
import org.springframework.http.HttpStatus;

/**
 * 댓글 이유 코드 (007 data-model §4-3, FR-008). 문구 끝에 마침표를 붙이지 않는다(README "정해진 것").
 *
 * <p>{@link #COMMENT_REQUIRED}·{@link #COMMENT_TOO_LONG}·{@link #REPLY_TARGET_UNAVAILABLE}는 공통
 * {@code VALIDATION_FAILED} 본문의 칸 오류({@code errors[]})로 싣는다. {@link #COMMENT_HIDDEN}만 최상위 {@code
 * code}다(409). 요청 과다는 공통 {@code TOO_MANY_REQUESTS}(007 Clarifications Q3).
 */
public enum CommentReasonCode implements ReasonCode {
    /** 정리한 내용이 빈 값 (field {@code content}). */
    COMMENT_REQUIRED(HttpStatus.BAD_REQUEST, "댓글 내용을 입력해 주세요"),
    /** 코드 포인트 1000 초과 (field {@code content}). */
    COMMENT_TOO_LONG(HttpStatus.BAD_REQUEST, "댓글은 1000자까지 쓸 수 있어요"),
    /** 대상이 없음·다른 글·삭제·숨김·작성자 탈퇴 (field {@code replyToCommentId}). */
    REPLY_TARGET_UNAVAILABLE(HttpStatus.BAD_REQUEST, "답글을 달 수 없는 댓글이에요"),
    /** 숨긴 내 댓글 수정. */
    COMMENT_HIDDEN(HttpStatus.CONFLICT, "숨겨진 댓글은 수정할 수 없어요");

    private final HttpStatus status;
    private final String defaultMessage;

    CommentReasonCode(HttpStatus status, String defaultMessage) {
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

    /** 이 코드를 그 칸의 오류로. */
    public FieldError at(String field) {
        return new FieldError(field, name(), defaultMessage);
    }
}
