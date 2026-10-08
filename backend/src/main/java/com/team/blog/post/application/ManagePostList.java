package com.team.blog.post.application;

import com.team.blog.post.infra.ManagePostRow;
import java.time.Instant;
import java.util.List;

/**
 * 관리 목록 한 페이지 (006 T042). 컨트롤러가 응답 DTO({@code ManagePostPage})로 옮긴다.
 *
 * @param items 줄 (최대 페이지 크기)
 * @param nextCursor 다음 페이지 커서 (없으면 {@code null})
 * @param counts 탭별 글 수 — 첫 요청(커서 없음)에만 값, 이어 보기에서는 {@code null}
 */
public record ManagePostList(List<Item> items, String nextCursor, Counts counts) {

    public ManagePostList {
        items = List.copyOf(items);
    }

    /**
     * @param row 조회한 줄
     * @param purgeAt 휴지통 줄이면 {@code deletedAt + 보관 기간}, 아니면 {@code null}
     */
    public record Item(ManagePostRow row, Instant purgeAt) {}

    /** 탭별 글 수 (숨긴 글 포함, 공개 범위 필터 무관 — FR-004). */
    public record Counts(long drafts, long published, long trash) {}
}
