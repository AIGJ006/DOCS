package com.team.blog.moderation.web.dto;

/**
 * {@code PUT /api/admin/{posts|comments}/{id}/hidden} 본문.
 *
 * @param reason 숨김 사유 코드 6개 중 하나
 */
public record HideRequest(Object reason) {}
