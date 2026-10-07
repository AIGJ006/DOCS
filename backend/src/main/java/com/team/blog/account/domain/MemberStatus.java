package com.team.blog.account.domain;

/** 회원 상태 ({@code member.status}, data-model §4-1). */
public enum MemberStatus {
    ACTIVE,
    SUSPENDED,
    /** 탈퇴 유예(015). 30일 뒤 익명 처리되면 {@code deleted_at}이 채워진다. */
    WITHDRAWN
}
