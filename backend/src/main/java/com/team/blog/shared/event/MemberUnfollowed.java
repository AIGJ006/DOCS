package com.team.blog.shared.event;

import java.time.Instant;

/**
 * 팔로우 관계가 실제로 지워짐 (010 data-model §6, contracts/follow-sql.md §5, EV-4). 팔로우하지 않은 상태의 요청과 탈퇴 정리(015
 * order 65)는 발행하지 않는다. 구독: 011 안 읽은 새 팔로워 묶음에서 빼기.
 *
 * @param followerId 언팔로우한 회원 번호
 * @param followeeId 언팔로우된 회원 번호
 * @param unfollowedAt 언팔로우한 시각
 */
public record MemberUnfollowed(long followerId, long followeeId, Instant unfollowedAt)
        implements DomainEvent {}
