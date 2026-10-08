package com.team.blog.moderation.web.dto;

/**
 * {@code POST /api/admin/members/{handle}/suspensions} 본문.
 *
 * @param duration {@code P1D}·{@code P7D}·{@code P30D}·{@code PERMANENT}
 * @param reason 사유 (앞뒤 공백 제거 뒤 1~200자)
 */
public record SuspendRequest(Object duration, Object reason) {}
