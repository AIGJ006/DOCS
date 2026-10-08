package com.team.blog.shared.event;

import java.time.Instant;

/** 휴지통에서 복구함 — {@code deleted_at}이 값에서 NULL로 바뀐 때만 (006 contracts/events.md §1). */
public record PostRestored(long postId, long authorId, Instant restoredAt) implements DomainEvent {}
