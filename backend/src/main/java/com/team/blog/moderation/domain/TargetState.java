package com.team.blog.moderation.domain;

/**
 * 처리 화면의 "현재: …" 상태 (research R7 표). 글은 {@code PUBLIC}·{@code PRIVATE}·{@code TRASHED}·{@code
 * HIDDEN}·{@code AUTHOR_WITHDRAWN}·{@code GONE}, 댓글은 {@code VISIBLE}·{@code DELETED}·{@code
 * POST_NOT_VISIBLE}과 {@code HIDDEN}·{@code AUTHOR_WITHDRAWN}·{@code GONE}을 쓴다.
 */
public enum TargetState {
    PUBLIC,
    PRIVATE,
    TRASHED,
    HIDDEN,
    AUTHOR_WITHDRAWN,
    GONE,
    VISIBLE,
    DELETED,
    POST_NOT_VISIBLE
}
