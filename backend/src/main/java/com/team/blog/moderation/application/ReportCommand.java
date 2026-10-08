package com.team.blog.moderation.application;

import com.team.blog.moderation.domain.ReportReason;
import com.team.blog.shared.event.ReportTargetType;

/**
 * 형식 검사를 마친 신고 (research R3 ③).
 *
 * @param detail 앞뒤 공백을 지운 설명 (없으면 {@code null}). 기타가 아니면 저장하지 않는다
 */
public record ReportCommand(
        ReportTargetType targetType, long targetId, ReportReason reason, String detail) {}
