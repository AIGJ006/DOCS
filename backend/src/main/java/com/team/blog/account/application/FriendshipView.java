package com.team.blog.account.application;

/**
 * 나와 그 회원의 관계 (openapi {@code FriendshipView}). 최근 활동({@code lastActive})은 US8에서 더한다.
 *
 * @param otherId 상대 회원 번호(응답에는 나가지 않는다)
 */
public record FriendshipView(FriendshipState status, long otherId) {}
