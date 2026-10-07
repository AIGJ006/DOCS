package com.team.blog.account.infra.redis;

/** 메일 링크 토큰 종류와 Redis 키 이름 (data-model §3). */
public enum TokenType {
    /** 이메일 인증 ({@code auth:verify:{token}}, 24시간). */
    VERIFY("auth:verify:", "auth:verify-latest:"),
    /** 비밀번호 재설정 ({@code auth:reset:{token}}, 30분 — US4). */
    RESET("auth:reset:", "auth:reset-latest:");

    private final String tokenKeyPrefix;
    private final String latestKeyPrefix;

    TokenType(String tokenKeyPrefix, String latestKeyPrefix) {
        this.tokenKeyPrefix = tokenKeyPrefix;
        this.latestKeyPrefix = latestKeyPrefix;
    }

    String tokenKey(String token) {
        return tokenKeyPrefix + token;
    }

    String latestKey(long memberId) {
        return latestKeyPrefix + memberId;
    }
}
