package com.team.blog.discovery.application.search;

import com.team.blog.discovery.application.PostCardView;
import java.time.Instant;

/**
 * 글 검색 결과 한 줄 (openapi {@code PostSearchItem}): 005 카드 필드 + {@code snippet}을 평평하게 둔다.
 *
 * <p>(구현 메모) data-model §5는 {@code PostSearchItem(card, snippet)}이지만 JSON이 평평해야 해서 카드 필드를 그대로 펼친
 * record로 둔다. 카드 필드가 바뀌면 {@link #of}도 함께 바꾼다(T044 계약 비교 시험이 막는다).
 */
public record PostSearchItem(
        long id,
        String url,
        String title,
        String excerpt,
        String thumbnailUrl,
        Instant firstPublicAt,
        int commentCount,
        int likeCount,
        PostCardView.Author author,
        Snippet snippet) {

    public static PostSearchItem of(PostCardView card, Snippet snippet) {
        return new PostSearchItem(
                card.id(),
                card.url(),
                card.title(),
                card.excerpt(),
                card.thumbnailUrl(),
                card.firstPublicAt(),
                card.commentCount(),
                card.likeCount(),
                card.author(),
                snippet);
    }
}
