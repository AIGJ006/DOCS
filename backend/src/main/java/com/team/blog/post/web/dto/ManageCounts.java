package com.team.blog.post.web.dto;

import com.team.blog.post.application.ManagePostList;

/** 탭별 글 수 (006 T040, FR-004). 발행 글 수는 숨긴 글을 포함하고 공개 범위 필터와 무관하다. */
public record ManageCounts(long drafts, long published, long trash) {

    public static ManageCounts from(ManagePostList.Counts counts) {
        return counts == null
                ? null
                : new ManageCounts(counts.drafts(), counts.published(), counts.trash());
    }
}
