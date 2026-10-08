package com.team.blog.account.application;

import java.util.List;

/** 커서 목록 한 페이지. {@code nextCursor}가 null이면 마지막. */
public record FriendListPage(List<FriendListItem> items, String nextCursor) {}
