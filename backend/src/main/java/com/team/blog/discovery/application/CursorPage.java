package com.team.blog.discovery.application;

import java.util.List;

/**
 * 커서 목록 한 페이지 (contracts {@code PostCardPage}).
 *
 * @param items 이번 페이지 (최대 {@code blog.list.page-size}개)
 * @param nextCursor 더 있으면 다음 위치, 없으면 {@code null}
 */
public record CursorPage<T>(List<T> items, String nextCursor) {

    public CursorPage {
        items = List.copyOf(items);
    }
}
