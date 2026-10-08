package com.team.blog.account.application;

/** 나와 그 회원의 관계 (openapi {@code FriendshipView.status}). */
public enum FriendshipState {
    SELF,
    NONE,
    REQUEST_SENT,
    REQUEST_RECEIVED,
    FRIENDS
}
