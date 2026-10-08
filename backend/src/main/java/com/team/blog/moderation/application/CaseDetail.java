package com.team.blog.moderation.application;

import com.team.blog.moderation.domain.CaseStatus;
import com.team.blog.moderation.domain.ReportReason;
import com.team.blog.moderation.domain.TargetState;
import com.team.blog.shared.event.ReportTargetType;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 관리자 사건 상세 (contracts {@code CaseDetail}, data-model §4). 현재 원문은 싣지 않는다 — 스냅샷과 현재 상태 이름만(FR-015).
 * 신고자 번호·이름은 없고 수·기타 설명·시각만 있다.
 *
 * @param postId 글 대상이면 글 번호 (사라졌으면 {@code null})
 * @param commentId 댓글 대상이면 댓글 번호 (사라졌으면 {@code null})
 * @param reportedByMe 이 관리자도 신고했나
 * @param onlyMyReport 신고자가 이 관리자뿐인가 — 그러면 처리할 수 없다
 */
public record CaseDetail(
        long caseId,
        ReportTargetType targetType,
        Long postId,
        Long commentId,
        String snapshotTitle,
        String snapshotContent,
        TargetState currentState,
        CaseStatus status,
        Instant createdAt,
        Instant handledAt,
        String handledByNickname,
        int reportCount,
        Map<ReportReason, Integer> reasonCounts,
        List<OtherDetail> otherDetails,
        boolean reportedByMe,
        boolean onlyMyReport,
        AuthorInfo author) {

    /** 기타 설명 하나 (신고자 정보 없음). {@code detail}은 30일 정리 뒤 {@code null}. */
    public record OtherDetail(String detail, Instant reportedAt) {}

    /**
     * 대상 작성자 카드.
     *
     * @param handle 익명 처리면 {@code null}
     * @param hiddenCount 숨김으로 닫힌 사건 수
     * @param suspendedNow 지금 정지 중인가
     * @param suspensionCount 정지 이력 수
     */
    public record AuthorInfo(
            String handle,
            String nickname,
            Instant joinedAt,
            int hiddenCount,
            boolean suspendedNow,
            int suspensionCount) {}
}
