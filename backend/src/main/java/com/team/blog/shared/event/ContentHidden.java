package com.team.blog.shared.event;

import java.time.Instant;

/**
 * 관리자가 글·댓글을 숨김 (014 data-model §6, 20 §3-5 — 011이 먼저 만듦, 011 T045). 신고자·관리자 번호는 싣지 않는다. 구독: 011
 * 작성자에게 {@code CONTENT_HIDDEN} 알림(댓글이면 그 댓글의 댓글·답글 알림 삭제).
 *
 * @param targetId 글 또는 댓글 번호
 * @param ownerId 그 글·댓글의 작성자
 * @param postId 글 번호 (댓글이면 그 댓글이 달린 글)
 */
public record ContentHidden(
        ReportTargetType targetType, long targetId, long ownerId, long postId, Instant hiddenAt)
        implements DomainEvent {}
