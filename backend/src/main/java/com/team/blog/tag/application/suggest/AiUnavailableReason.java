package com.team.blog.tag.application.suggest;

/** 503 {@code AI_UNAVAILABLE}의 {@code details.reason} (013 plan 설계 후 확인 2 — 팀 확인 T003 ②). */
public enum AiUnavailableReason {
    /** 기능 꺼짐 ({@code blog.ai.tag-suggest.enabled = false}). */
    DISABLED,
    /** 공급자 시간 초과·서버 오류·형식 깨짐, 쓸 수 있는 공급자 없음. */
    FAILED,
    /** 자체 AI 동시 처리 초과 — 화면 "잠시 후 다시 시도해 주세요". */
    BUSY,
    /** Redis 장애·메모리 부족. */
    STORE_UNAVAILABLE
}
