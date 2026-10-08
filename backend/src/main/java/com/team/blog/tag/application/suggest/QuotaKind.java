package com.team.blog.tag.application.suggest;

/** Gemini 429의 한도 종류 (contracts/providers.md §3). */
public enum QuotaKind {
    /** 하루 한도 ({@code quotaId}에 {@code PerDay}). */
    PER_DAY,
    /** 분당 한도 ({@code quotaId}에 {@code PerMinute}). */
    PER_MINUTE,
    /** 종류를 찾지 못함. */
    UNKNOWN
}
