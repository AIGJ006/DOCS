package com.team.blog.shared.event;

import java.time.Instant;

/**
 * 글을 휴지통으로 옮김 — {@code deleted_at}이 NULL에서 값으로 바뀐 때만 (006 contracts/events.md §1, 20 §3-1). 이미 휴지통인
 * 글을 다시 지운 경우·빈 임시글 즉시 삭제는 발행하지 않는다. 제목·본문은 싣지 않는다(EV-3).
 */
public record PostTrashed(long postId, long authorId, Instant trashedAt) implements DomainEvent {}
