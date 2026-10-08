package com.team.blog.account.application;

import java.time.Instant;

/**
 * 친구 목록·받은 요청 목록 한 줄 (openapi {@code FriendItem}·{@code FriendRequestItem}).
 *
 * @param at 친구가 된 시각({@code friendsSince}) 또는 요청 시각({@code requestedAt})
 */
public record FriendListItem(
        long memberId, String handle, String nickname, String profileImageUrl, Instant at) {}
