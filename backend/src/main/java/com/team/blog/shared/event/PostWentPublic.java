package com.team.blog.shared.event;

import java.time.Instant;

/**
 * 이번에 {@code first_public_at}을 처음 채움 — 공개로 최초 발행, 비공개였던 글을 다시 발행하며 처음 공개, 또는 004 공개 범위 변경 (20 §3-1
 * EV-5). 002 발행과 004가 함께 쓴다.
 */
public record PostWentPublic(long postId, long authorId, Instant firstPublicAt)
        implements DomainEvent {}
