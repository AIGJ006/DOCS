package com.team.blog.shared.event;

/** 신고 처리 결과 (014 data-model §6 — 011이 먼저 만듦). V1 {@code ck_notification_result}와 같은 값. */
public enum ReportResult {
    /** 조치함. */
    ACTION_TAKEN,
    /** 문제없음. */
    NO_VIOLATION
}
