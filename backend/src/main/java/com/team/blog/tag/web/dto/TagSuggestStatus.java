package com.team.blog.tag.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.team.blog.tag.application.suggest.Provider;
import com.team.blog.tag.application.suggest.TagSuggestStatusView;

/**
 * {@code GET /api/posts/{postId}/tag-suggestions/status} 200 — {@code provider}는 쓸 수 없으면 {@code
 * null}.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record TagSuggestStatus(
        boolean available,
        boolean consentRequired,
        String consentVersion,
        Provider provider,
        int remainingToday) {

    public static TagSuggestStatus from(TagSuggestStatusView v) {
        return new TagSuggestStatus(
                v.available(),
                v.consentRequired(),
                v.consentVersion(),
                v.provider(),
                v.remainingToday());
    }
}
