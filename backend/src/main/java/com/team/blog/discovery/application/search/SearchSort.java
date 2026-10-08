package com.team.blog.discovery.application.search;

import com.team.blog.shared.error.ValidationException;
import java.util.Locale;

/** 글 검색 정렬 (012 data-model §3, FR-027·FR-029). 주소 값은 소문자 {@code relevance}·{@code latest}. */
public enum SearchSort {
    RELEVANCE,
    LATEST;

    /** 주소 값 (커서 목록 구분에도 쓴다). */
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * @param raw 요청 값 (없거나 빈 값이면 관련도순)
     * @throws ValidationException 모르는 값 (400 {@code VALIDATION_FAILED})
     */
    public static SearchSort parse(String raw) {
        if (raw == null || raw.isEmpty()) {
            return RELEVANCE;
        }
        for (SearchSort sort : values()) {
            if (sort.value().equals(raw)) {
                return sort;
            }
        }
        throw ValidationException.of("sort", "INVALID_SORT", "정렬 값을 확인해 주세요");
    }
}
