package com.team.blog.moderation.domain;

/** 신고 사건 상태 (V1 {@code ck_report_case_status}, data-model §1-1). {@code PENDING}만 바뀐다. */
public enum CaseStatus {
    PENDING,
    HIDDEN,
    REJECTED,
    CLOSED_NO_TARGET
}
