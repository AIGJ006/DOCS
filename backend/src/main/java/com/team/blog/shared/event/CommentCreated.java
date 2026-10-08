package com.team.blog.shared.event;

import java.time.Instant;

/**
 * 새 댓글·답글이 생김 (007 contracts/events.md §1-1, 20 §3-2). 10초 안 같은 요청으로 기존 댓글을 돌려줄 때는 발행하지 않는다. 내용은 싣지
 * 않는다(EV-3). 011 알림이 커밋 후 받는다.
 *
 * @param parentId 답글이면 최상위 번호
 * @param parentAuthorId 답글이면 최상위 작성자
 * @param replyToMemberId 답글의 답글이고 대상이 남일 때 그 회원
 */
public record CommentCreated(
        long commentId,
        long postId,
        long postAuthorId,
        long authorId,
        Long parentId,
        Long parentAuthorId,
        Long replyToMemberId,
        Instant createdAt)
        implements DomainEvent {}
