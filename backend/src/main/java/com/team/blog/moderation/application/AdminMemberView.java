package com.team.blog.moderation.application;

import com.team.blog.account.application.SuspensionRecord;
import com.team.blog.account.domain.MemberStatus;
import com.team.blog.account.domain.Role;
import java.time.Instant;
import java.util.List;

/**
 * 관리자 회원 화면 (contracts {@code AdminMemberView}, data-model §4).
 *
 * @param hiddenCount 숨김으로 닫힌 사건 수
 * @param openSuspension 열린 정지 (없으면 {@code null})
 * @param history 정지 이력 최근 순 (최대 50)
 */
public record AdminMemberView(
        String handle,
        String nickname,
        Role role,
        MemberStatus status,
        Instant joinedAt,
        int hiddenCount,
        SuspensionRecord openSuspension,
        List<SuspensionRecord> history) {}
