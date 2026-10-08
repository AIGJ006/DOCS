package com.team.blog.shared.event;

import java.time.Instant;

/**
 * 작성자가 댓글을 지움 — 자리로 남겨도 발행한다 (007 contracts/events.md §1-2, 20 §3-2). 빈 자리 정리로 최상위가 함께 지워지면 그 최상위에
 * 대해서도 한 번 더 발행한다. 글 완전 삭제의 CASCADE·탈퇴 정리·숨김은 발행하지 않는다. 011 알림·014 신고가 받는다.
 *
 * @param authorId 지운 댓글의 작성자
 * @param parentId 답글이면 최상위 번호
 */
public record CommentDeleted(
        long commentId,
        long postId,
        long postAuthorId,
        long authorId,
        Long parentId,
        Instant deletedAt)
        implements DomainEvent {}
