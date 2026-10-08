package com.team.blog.moderation.web.dto;

/**
 * {@code POST /api/admin/reports/{caseId}/resolution} 본문. 형식은 Service가 검사한다(어떤 JSON 값이 와도 본문 읽기에서
 * 실패하지 않게).
 *
 * @param action {@code HIDE}·{@code REJECT}
 * @param reason HIDE면 사유 코드 6개 중 하나 (REJECT면 무시)
 */
public record ResolutionRequest(Object action, Object reason) {}
