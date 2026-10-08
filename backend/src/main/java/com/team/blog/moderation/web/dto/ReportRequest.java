package com.team.blog.moderation.web.dto;

/**
 * {@code POST /api/reports} 본문 (contracts {@code ReportRequest}). 칸 형식은 판정 순서(401 → 403 → 400)를
 * 지키려고 계정 상태 확인 뒤 Service가 검사한다 — 그래서 모든 칸을 JSON 값 그대로 받는다(문자열·숫자 어느 쪽이든 본문 읽기에서 실패하지 않게).
 *
 * @param targetType {@code POST}·{@code COMMENT}
 * @param targetId 1 이상 정수
 * @param reason 사유 코드 6개 중 하나
 * @param detail 기타일 때 설명 (200자 이하)
 */
public record ReportRequest(Object targetType, Object targetId, Object reason, Object detail) {}
