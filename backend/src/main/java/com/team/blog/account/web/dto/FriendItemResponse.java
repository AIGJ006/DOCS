package com.team.blog.account.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.team.blog.account.application.FriendListItem;
import java.time.Instant;

/**
 * openapi {@code FriendItem}. {@code profileImageUrl}은 null이어도 키가 있고, {@code lastActive}는 표시 조건을
 * 만족할 때만 키가 있다(US8).
 */
public record FriendItemResponse(
        String handle,
        String nickname,
        String profileImageUrl,
        Instant friendsSince,
        @JsonInclude(JsonInclude.Include.NON_NULL) Object lastActive) {

    public static FriendItemResponse of(FriendListItem item) {
        return new FriendItemResponse(
                item.handle(), item.nickname(), item.profileImageUrl(), item.at(), null);
    }
}
