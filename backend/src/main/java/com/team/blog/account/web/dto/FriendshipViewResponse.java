package com.team.blog.account.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.team.blog.account.application.FriendshipView;
import com.team.blog.account.domain.LastActiveView;

/**
 * openapi {@code FriendshipView}. {@code lastActive}는 친구 + 둘 다 공개 + 값 있음일 때만 키가 있다 — 아니면 키 자체가
 * 없다(FR-060).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FriendshipViewResponse(String status, LastActiveView lastActive) {

    public static FriendshipViewResponse of(FriendshipView view) {
        return new FriendshipViewResponse(view.status().name(), view.lastActive());
    }
}
