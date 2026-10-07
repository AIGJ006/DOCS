package com.team.blog.support.permission;

/**
 * 권한 매트릭스의 행위자 (42 §2). 대상 글과의 관계까지 포함한다.
 *
 * <ul>
 *   <li>{@link #UNVERIFIED}·{@link #MEMBER}·{@link #ADMIN}은 대상 글의 작성자가 아닌 다른 회원이다(42 §5-1·§5-2의
 *       "회원(남의 글)", "관리자(남의 글)").
 *   <li>{@link #AUTHOR}·{@link #UNVERIFIED_AUTHOR}·{@link #SUSPENDED}·{@link #WITHDRAWN}은 대상 글의 작성자
 *       본인이며 계정 상태만 다르다. 42 §5-2의 "인증 전" 칸(자기 글 삭제·복구 허용)은 {@link #UNVERIFIED_AUTHOR}로 적는다. 정지·탈퇴
 *       유예는 세션 삭제와 경쟁해 남은 세션(H7, P-12)을 재현한다.
 * </ul>
 */
public enum Actor {
    /** 비회원 (세션 없음). */
    ANONYMOUS(false),
    /** 이메일 인증 전 회원, 작성자 아님. */
    UNVERIFIED(false),
    /** 회원(인증 완료·ACTIVE), 작성자 아님. */
    MEMBER(false),
    /** 작성자 본인(인증 완료·ACTIVE). */
    AUTHOR(true),
    /** 관리자, 작성자 아님. */
    ADMIN(false),
    /** 이메일 인증 전인 작성자 본인. */
    UNVERIFIED_AUTHOR(true),
    /** 정지된 작성자 본인의 남은 세션. */
    SUSPENDED(true),
    /** 탈퇴 유예 중인 작성자 본인의 세션. */
    WITHDRAWN(true);

    private final boolean author;

    Actor(boolean author) {
        this.author = author;
    }

    /** 대상 글의 작성자 본인으로 로그인하는가. */
    public boolean isAuthor() {
        return author;
    }
}
