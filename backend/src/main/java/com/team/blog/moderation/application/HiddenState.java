package com.team.blog.moderation.application;

/**
 * 직접 숨김·해제 응답 (contracts {@code HiddenState}).
 *
 * @param caseId 이번에 숨김으로 닫은 사건 (해제·이미 숨김이면 {@code null})
 */
public record HiddenState(boolean hidden, Long caseId) {}
