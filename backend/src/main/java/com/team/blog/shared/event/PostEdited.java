package com.team.blog.shared.event;

import java.time.Instant;

/** 다시 발행 — 이미 {@code published_at}이 있음 (002 contracts/events.md §1, 20 §3-1). 필드는 ID·시각만(EV-3). */
public record PostEdited(long postId, long authorId, Instant editedAt) implements DomainEvent {}
