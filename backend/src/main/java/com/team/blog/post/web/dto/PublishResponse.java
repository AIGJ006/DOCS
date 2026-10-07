package com.team.blog.post.web.dto;

import com.team.blog.post.domain.PublishResult;
import java.time.Instant;

/** 발행 응답 (contracts {@code PublishResponse}). */
public record PublishResponse(
        String url, Instant publishedAt, Instant firstPublicAt, Instant editedAt, long version) {

    public static PublishResponse from(PublishResult r) {
        return new PublishResponse(
                r.url(), r.publishedAt(), r.firstPublicAt(), r.editedAt(), r.version());
    }
}
