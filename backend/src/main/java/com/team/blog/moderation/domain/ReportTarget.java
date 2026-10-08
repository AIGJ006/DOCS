package com.team.blog.moderation.domain;

import com.team.blog.shared.event.ReportTargetType;

/**
 * 신고·직접 숨김 대상 (data-model §3). 볼 수 있는지 확인을 마친 것만 만든다({@code ReportTargetResolver}).
 *
 * @param targetId 글 또는 댓글 번호
 * @param postId 글 번호 (댓글이면 그 댓글의 글)
 * @param authorId 대상 작성자
 * @param snapshotTitle 글 제목 (댓글은 {@code null})
 * @param snapshotContent 글 원문 앞부분 / 댓글 내용
 */
public record ReportTarget(
        ReportTargetType type,
        long targetId,
        long postId,
        long authorId,
        String snapshotTitle,
        String snapshotContent) {

    public boolean isPost() {
        return type == ReportTargetType.POST;
    }
}
