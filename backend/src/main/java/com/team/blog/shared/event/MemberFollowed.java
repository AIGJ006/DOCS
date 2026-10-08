package com.team.blog.shared.event;

import java.time.Instant;

/**
 * 팔로우 관계가 실제로 생김 (010 data-model §6, contracts/follow-sql.md §5, EV-4). 이미 팔로우 중이던 요청은 발행하지 않는다.
 * 구독: 011 새 팔로워 알림(같은 사람 7일 1번).
 *
 * @param followerId 팔로우한 회원 번호
 * @param followeeId 팔로우받은 회원 번호
 * @param followedAt 팔로우한 시각
 */
public record MemberFollowed(long followerId, long followeeId, Instant followedAt)
        implements DomainEvent {}
