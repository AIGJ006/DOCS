package com.team.blog.account.application;

import com.team.blog.account.domain.MemberStatus;
import com.team.blog.account.domain.Role;

/**
 * 권한 판정용 회원 정보 (004 {@code Viewer}가 만든다). {@link MemberQueryService#findAccessInfo(long)}가 PK 1번으로
 * 읽는다.
 *
 * <p>{@code WithdrawnAccountGateFilter}가 요청마다 읽은 값을 {@link #REQUEST_ATTRIBUTE}에 두므로 같은 요청 안에서는 다시
 * 조회하지 않아도 된다(004 T011 {@code CurrentViewerResolver}).
 */
public record MemberAccessInfo(Role role, MemberStatus status, boolean emailVerified) {

    /** 요청 attribute 이름 — 값은 {@code MemberAccessInfo}. 로그인 요청에서만 채워진다. */
    public static final String REQUEST_ATTRIBUTE = MemberAccessInfo.class.getName();

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
