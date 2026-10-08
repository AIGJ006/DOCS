package com.team.blog.post.application;

/**
 * 신고·직접 숨김 때 복사할 글 내용 (014 data-model §3). {@code contentHead}는 원문({@code content_md}) 앞 2,000 코드
 * 포인트까지다.
 *
 * @param hidden 지금 숨김인가
 * @param trashed 지금 휴지통에 있는가
 */
public record PostSnapshot(
        long postId,
        long authorId,
        String title,
        String contentHead,
        boolean hidden,
        boolean trashed) {}
