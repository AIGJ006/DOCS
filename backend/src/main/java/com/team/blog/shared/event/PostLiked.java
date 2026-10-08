package com.team.blog.shared.event;

import java.time.Instant;

/**
 * 좋아요가 실제로 생김 (009 data-model §5, 20 §3-3, FR-012). 이미 좋아요 상태였던 요청은 발행하지 않는다. 구독: 011 알림(같은 사람·같은 글
 * 1번), 012 트렌딩.
 *
 * @param postId 글 번호
 * @param postAuthorId 글 작성자 회원 번호
 * @param memberId 누른 회원 번호
 * @param likedAt 누른 시각
 */
public record PostLiked(long postId, long postAuthorId, long memberId, Instant likedAt)
        implements DomainEvent {}
