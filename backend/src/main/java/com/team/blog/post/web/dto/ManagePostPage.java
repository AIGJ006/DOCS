package com.team.blog.post.web.dto;

import com.team.blog.post.application.ManagePostList;
import java.util.List;

/**
 * 관리 목록 응답 (006 T040, contracts {@code ManagePostPage}). {@code nextCursor}가 {@code null}이면 마지막
 * 페이지이고, {@code counts}는 첫 요청(커서 없음)에만 값이며 [더 보기]에서는 {@code null}이다.
 */
public record ManagePostPage(List<ManagePostItem> items, String nextCursor, ManageCounts counts) {

    public static ManagePostPage from(ManagePostList list) {
        return new ManagePostPage(
                list.items().stream().map(ManagePostItem::from).toList(),
                list.nextCursor(),
                ManageCounts.from(list.counts()));
    }
}
