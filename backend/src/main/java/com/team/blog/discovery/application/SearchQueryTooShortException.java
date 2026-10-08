package com.team.blog.discovery.application;

import com.team.blog.shared.error.BusinessRuleException;

/** 정리 뒤 남는 단어가 없음 → 400 {@code SEARCH_QUERY_TOO_SHORT} (012 research R6·R10, FR-024·FR-036). */
public class SearchQueryTooShortException extends BusinessRuleException {

    public SearchQueryTooShortException() {
        super(DiscoveryReasonCode.SEARCH_QUERY_TOO_SHORT);
    }
}
