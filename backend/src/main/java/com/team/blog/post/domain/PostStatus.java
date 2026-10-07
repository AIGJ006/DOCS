package com.team.blog.post.domain;

/**
 * 글 상태 ({@code post.status}, {@code ck_post_status}). 발행 → 임시글로 돌아가는 전이는 없다(002 P-1, FR-033) — 글을
 * 내리려면 공개 범위를 {@code PRIVATE}로 바꾼다.
 */
public enum PostStatus {
    /** 임시글. 작성자만 본다. */
    DRAFT,
    /** 발행됨. 공개 범위에 따라 보인다. */
    PUBLISHED
}
