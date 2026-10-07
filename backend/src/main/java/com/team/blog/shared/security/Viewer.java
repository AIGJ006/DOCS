package com.team.blog.shared.security;

import com.team.blog.account.domain.MemberStatus;
import com.team.blog.account.domain.Role;

/**
 * 지금 요청을 보낸 사람 (004 data-model §2). 세션의 회원 번호로만 만든다 — 요청 본문·쿼리의 회원 번호는 쓰지 않는다(FR-026, research
 * R-07). 컨트롤러는 {@link CurrentViewerResolver}가 넣어 주는 파라미터로만 받는다.
 *
 * @param id 회원 번호. 비회원이면 {@code null}
 * @param role 역할. 비회원이면 {@code null}
 * @param status 계정 상태. 비회원이면 {@code null}
 * @param emailVerified 이메일 인증 여부 (소셜 가입은 가입 때 인증됨)
 */
public record Viewer(Long id, Role role, MemberStatus status, boolean emailVerified) {

    private static final Viewer ANONYMOUS = new Viewer(null, null, null, false);

    /** 비회원 (세션 없음·세션 저장소 장애·익명 처리된 회원). */
    public static Viewer anonymous() {
        return ANONYMOUS;
    }

    public boolean isAuthenticated() {
        return id != null;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    /** 그 글·댓글의 작성자인가. 관리자도 자기 것이면 작성자다(42 §2). */
    public boolean isAuthorOf(long authorId) {
        return id != null && id == authorId;
    }
}
