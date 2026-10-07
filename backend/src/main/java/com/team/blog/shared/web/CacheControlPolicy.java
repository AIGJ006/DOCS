package com.team.blog.shared.web;

import com.team.blog.post.domain.PostStatus;
import com.team.blog.post.domain.Visibility;
import com.team.blog.post.infra.PostView;

/**
 * 글 응답의 {@code Cache-Control} (06 R-5, 40 R-9, research R-12·R-30, FR-015).
 *
 * <ul>
 *   <li>발행·{@code PUBLIC}·숨김 아님 → {@code private, no-cache} (공개 글도 상세는 매번 확인)
 *   <li>그 밖(비공개·임시글·숨긴 글 — 작성자가 보는 경우 포함) → {@code private, no-store}
 *   <li>404 → {@code private, no-store} (공개 전환이 캐시된 404에 가리지 않고, 볼 수 없는 글과 없는 글의 헤더가 같게)
 * </ul>
 *
 * 404 헤더는 001 {@code GlobalExceptionHandler}가 모든 {@code NotFoundException}에 붙인다.
 */
public final class CacheControlPolicy {

    public static final String NO_CACHE = "private, no-cache";
    public static final String NO_STORE = "private, no-store";

    private CacheControlPolicy() {}

    public static String forPost(PostStatus status, Visibility visibility, boolean hidden) {
        return status == PostStatus.PUBLISHED && visibility == Visibility.PUBLIC && !hidden
                ? NO_CACHE
                : NO_STORE;
    }

    public static String forPost(PostView post) {
        return forPost(post.status(), post.visibility(), post.isHidden());
    }

    public static String notFound() {
        return NO_STORE;
    }
}
