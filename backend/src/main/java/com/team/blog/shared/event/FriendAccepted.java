package com.team.blog.shared.event;

import java.time.Instant;

/**
 * 친구 요청이 수락됐다({@code PENDING → ACCEPTED}, 받은 요청 [수락]과 맞요청 즉시 수락 모두) (001 contracts/events.md §1).
 *
 * @param requesterId 처음 요청한 쪽({@code requested_by})
 */
public record FriendAccepted(long requesterId, long accepterId, Instant acceptedAt)
        implements DomainEvent {}
