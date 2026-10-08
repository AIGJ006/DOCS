package com.team.blog.interaction.application;

/**
 * 조회 기록 한 번의 결과 (009 contracts/view-pipeline.md §1). 응답은 모두 같은 204이고, 이 값은 조회수 로그(DEBUG)에만 남긴다 —
 * 로그에는 글 번호와 이 값만 쓰고 IP·UA·{@code vid}·방문자 키는 쓰지 않는다(FR-034).
 */
public enum ViewOutcome {
    /** 셈 (모음에 더함). */
    COUNTED,
    /** 기간 안 최대 횟수를 넘은 같은 방문자. */
    DUPLICATE,
    /** 작성자 본인 (관리자도 자기 글이면). */
    EXCLUDED_AUTHOR,
    /** 관리자 (FR-031). */
    EXCLUDED_ADMIN,
    /** User-Agent에 봇·미리보기 단어. */
    EXCLUDED_BOT,
    /** {@code Sec-Purpose}·{@code Purpose}에 {@code prefetch}. */
    EXCLUDED_PREFETCH,
    /** Redis 장애·메모리 부족으로 건너뜀. */
    SKIPPED_REDIS
}
