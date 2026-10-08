package com.team.blog.account.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.team.blog.account.application.FriendshipView;

/**
 * openapi {@code FriendshipView}. {@code lastActive}는 표시 조건을 만족할 때만 키가 있다(US8에서 채움 — 그 전에는 키 없음).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FriendshipViewResponse(String status, Object lastActive) {

    public static FriendshipViewResponse of(FriendshipView view) {
        return new FriendshipViewResponse(view.status().name(), null);
    }
}
