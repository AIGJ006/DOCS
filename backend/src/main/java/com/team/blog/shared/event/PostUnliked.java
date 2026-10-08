package com.team.blog.shared.event;

import java.time.Instant;

/**
 * 좋아요가 실제로 지워짐 (009 data-model §5, 20 §3-3, FR-012). 이미 취소 상태였던 요청·탈퇴 정리·글 완전 삭제는 발행하지 않는다. 구독: 012
 * 트렌딩.
 *
 * @param postId 글 번호
 * @param postAuthorId 글 작성자 회원 번호
 * @param memberId 취소한 회원 번호
 * @param unlikedAt 취소한 시각
 */
public record PostUnliked(long postId, long postAuthorId, long memberId, Instant unlikedAt)
        implements DomainEvent {}
