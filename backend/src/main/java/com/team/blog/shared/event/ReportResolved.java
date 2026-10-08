package com.team.blog.shared.event;

import java.time.Instant;

/**
 * 신고 한 건이 처리됨 (014 data-model §6, 20 §3-5 — 011이 먼저 만듦, 011 T045). 사건마다가 아니라 신고(신고자)마다 발행한다. 대상이
 * 사라져 닫힌 사건({@code CLOSED_NO_TARGET})은 발행하지 않는다. 구독: 011 신고자에게 {@code REPORT_RESOLVED} 알림.
 *
 * @param reportId 신고 번호 ({@code report.id})
 * @param reporterId 신고자
 * @param targetId 글 또는 댓글 번호
 */
public record ReportResolved(
        long reportId,
        long reporterId,
        ReportTargetType targetType,
        long targetId,
        ReportResult result,
        Instant resolvedAt)
        implements DomainEvent {}
