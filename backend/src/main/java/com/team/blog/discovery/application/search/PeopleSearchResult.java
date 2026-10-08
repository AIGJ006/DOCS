package com.team.blog.discovery.application.search;

import java.util.List;

/** 사람 검색 결과 (openapi {@code PeopleSearchResult}) — 최대 {@code blog.search.people-limit}명, 커서 없음. */
public record PeopleSearchResult(List<PersonItem> items) {

    public PeopleSearchResult {
        items = List.copyOf(items);
    }
}
