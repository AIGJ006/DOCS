package com.team.blog.shared.event;

import com.team.blog.post.domain.Visibility;
import java.time.Instant;

/**
 * 발행된 글의 공개 범위가 실제로 바뀜 — 004 {@code PUT /api/posts/{postId}/visibility} (contracts/events.md, 20
 * §3-1, research R-11·R-25). 같은 값을 다시 보낸 경우와 임시글의 값 변경에는 발행하지 않는다. {@code changedAt}은 {@code
 * post.updated_at}과 같은 값이다.
 */
public record PostVisibilityChanged(
        long postId, long authorId, Visibility from, Visibility to, Instant changedAt)
        implements DomainEvent {}
