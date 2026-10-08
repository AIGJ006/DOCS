package com.team.blog.account.web.dto;

import java.util.List;

/** {@code {items, nextCursor}} — {@code nextCursor}가 null이면 마지막 페이지. */
public record FriendListResponse<T>(List<T> items, String nextCursor) {}
