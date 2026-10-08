package com.team.blog.account.web.dto;

import com.team.blog.account.application.FriendListItem;
import java.time.Instant;

/** openapi {@code FriendRequestItem}. */
public record FriendRequestItemResponse(
        String handle, String nickname, String profileImageUrl, Instant requestedAt) {

    public static FriendRequestItemResponse of(FriendListItem item) {
        return new FriendRequestItemResponse(
                item.handle(), item.nickname(), item.profileImageUrl(), item.at());
    }
}
