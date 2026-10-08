package com.team.blog.moderation.application;

import java.util.List;

/** 관리자 사건 목록 한 페이지 ({@code {items, nextCursor}}). */
public record CasePage(List<CaseListItem> items, String nextCursor) {}
