package com.team.blog.shared.event;

import java.time.Instant;

/**
 * 관리자가 글·댓글의 숨김을 해제함 (014 data-model §6, 20 §3-5). 알림을 만들지 않는다(FR-030) — 지금 구독자 없음. 사건 상태는 그대로
 * {@code HIDDEN}이다.
 *
 * @param targetId 글 또는 댓글 번호
 * @param ownerId 그 글·댓글의 작성자
 * @param postId 글 번호 (댓글이면 그 댓글이 달린 글)
 */
public record ContentUnhidden(
        ReportTargetType targetType, long targetId, long ownerId, long postId, Instant unhiddenAt)
        implements DomainEvent {}
