package com.team.blog.notification.domain;

/**
 * 알림 종류 7개 (011 data-model §2, V1 {@code ck_notification_type}과 같은 순서). 친구 알림 2종은 공통에 없다(선택 기능,
 * FR-040).
 */
public enum NotificationType {
    /** 내 글에 달린 댓글. */
    COMMENT,
    /** 내 댓글에 달린 답글. */
    REPLY,
    /** 내 글 좋아요 (글마다 묶음). */
    LIKE,
    /** 새 팔로워 (안 읽은 동안 묶음). */
    FOLLOW,
    /** 팔로우한 사람의 새 글. */
    NEW_POST,
    /** 내 신고의 처리 결과 (운영 알림). */
    REPORT_RESOLVED,
    /** 내 글·댓글 숨김 (운영 알림). */
    CONTENT_HIDDEN;

    /** 운영 알림인가 — 끌 수 없고 행동자가 없다(FR-022). */
    public boolean isOperational() {
        return this == REPORT_RESOLVED || this == CONTENT_HIDDEN;
    }

    /** 사람이 묶이는 알림인가 ({@code notification_actor}·{@code group_key}를 쓴다). */
    public boolean isGrouped() {
        return this == LIKE || this == FOLLOW;
    }

    /** 만들 때 받는 사람이 그 글을 읽을 수 있어야 하는가 (공통 제외 규칙 ⑤ — {@code NEW_POST}는 비회원 기준 한 번으로 대신). */
    public boolean needsReadablePost() {
        return this == COMMENT || this == REPLY || this == LIKE || this == NEW_POST;
    }

    /** 끌 수 있는 종류면 그 값, 운영 알림이면 {@code null}. */
    public MutableType mutable() {
        return isOperational() ? null : MutableType.valueOf(name());
    }
}
