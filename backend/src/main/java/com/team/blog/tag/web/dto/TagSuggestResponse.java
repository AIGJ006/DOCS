package com.team.blog.tag.web.dto;

import com.team.blog.tag.application.suggest.Provider;
import com.team.blog.tag.application.suggest.TagSuggestResult;
import java.util.List;

/** {@code POST /api/posts/{postId}/tag-suggestions} 200 (013 contracts/openapi.yaml). */
public record TagSuggestResponse(
        List<String> tags,
        Provider provider,
        boolean cached,
        boolean truncated,
        int remainingToday) {

    public static TagSuggestResponse from(TagSuggestResult r) {
        return new TagSuggestResponse(
                r.tags(), r.provider(), r.cached(), r.truncated(), r.remainingToday());
    }
}
