package com.team.blog.shared.event;

/**
 * 글 행을 완전히 지움 — 영구 삭제, 30일 휴지통 비우기, 탈퇴 정리(015) (006 contracts/events.md §1). 빈 임시글 즉시 삭제와 002 빈 임시글
 * 정리는 발행하지 않는다(누구에게도 보인 적 없는 글, 20 §3-1).
 */
public record PostPurged(long postId, long authorId) implements DomainEvent {}
