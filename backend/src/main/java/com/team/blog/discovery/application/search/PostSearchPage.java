package com.team.blog.discovery.application.search;

import java.util.List;

/**
 * 글 검색 한 페이지 (openapi {@code PostSearchPage}).
 *
 * @param items 이번 페이지 (최대 {@code blog.list.page-size}개)
 * @param nextCursor 더 있으면 다음 위치, 없으면 {@code null}
 * @param notice 2글자 단어가 있으면 {@value #TWO_CHAR_TITLE_TAG_ONLY}, 아니면 {@code null} (FR-024)
 */
public record PostSearchPage(List<PostSearchItem> items, String nextCursor, String notice) {

    public static final String TWO_CHAR_TITLE_TAG_ONLY = "TWO_CHAR_TITLE_TAG_ONLY";

    public PostSearchPage {
        items = List.copyOf(items);
    }
}
