package com.team.blog.shared.event;

import com.team.blog.post.domain.Visibility;
import java.time.Instant;

/**
 * 최초 발행 — {@code published_at}이 이번에 채워짐 (002 contracts/events.md §1, 20 §3-1). 필드는
 * ID·enum·시각만(EV-3).
 */
public record PostPublished(long postId, long authorId, Visibility visibility, Instant publishedAt)
        implements DomainEvent {}
