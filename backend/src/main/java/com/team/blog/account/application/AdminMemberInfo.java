package com.team.blog.account.application;

import com.team.blog.account.domain.MemberStatus;
import com.team.blog.account.domain.Role;
import java.time.Instant;

/**
 * 관리자 회원 화면이 읽는 회원 정보 (014 data-model §3, T014). 탈퇴 유예 회원도 담는다({@code status = WITHDRAWN}). 익명 처리된
 * 회원은 만들지 않는다.
 */
public record AdminMemberInfo(
        long id,
        String handle,
        String nickname,
        Role role,
        MemberStatus status,
        Instant createdAt,
        Instant withdrawnAt) {}
