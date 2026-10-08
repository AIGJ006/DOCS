package com.team.blog.moderation.application;

import com.team.blog.moderation.domain.CaseStatus;
import com.team.blog.moderation.domain.ReportReason;
import com.team.blog.shared.event.ReportTargetType;
import java.time.Instant;
import java.util.Map;

/**
 * 관리자 사건 목록 한 줄 (contracts {@code CaseListItem}, data-model §4).
 *
 * @param title 글: 스냅샷 제목, 댓글: 스냅샷 내용 앞 40자 (30일 정리 뒤 {@code null})
 * @param authorHandle 대상 작성자 주소 (익명 처리면 {@code null})
 * @param reportCount 신고 수 (직접 숨김 사건이면 0)
 * @param reasonCounts 사유별 신고 수 (0인 사유는 빠짐)
 * @param lastReportedAt 최근 신고 시각 (신고가 없으면 {@code null})
 * @param handledByNickname 처리 관리자 닉네임 (자동 종료면 {@code null} — 화면 "자동")
 * @param targetHiddenNow 대상이 지금 숨김인가 (처리됨 탭의 [숨김 해제])
 */
public record CaseListItem(
        long caseId,
        ReportTargetType targetType,
        String title,
        String authorHandle,
        int reportCount,
        Map<ReportReason, Integer> reasonCounts,
        Instant lastReportedAt,
        CaseStatus status,
        Instant handledAt,
        String handledByNickname,
        boolean targetHiddenNow) {}
