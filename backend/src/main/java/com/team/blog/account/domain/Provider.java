package com.team.blog.account.domain;

/**
 * 로그인 수단 ({@code auth_identity.provider}). 주소 접두어: LOCAL 없음, GOOGLE {@code go-}, GITHUB {@code gi-}
 * (08 H-1).
 */
public enum Provider {
    LOCAL(""),
    GOOGLE("go-"),
    GITHUB("gi-");

    private final String handlePrefix;

    Provider(String handlePrefix) {
        this.handlePrefix = handlePrefix;
    }

    /** 이 가입 수단의 블로그 주소 접두어 (이메일 가입은 빈 문자열). */
    public String handlePrefix() {
        return handlePrefix;
    }
}
