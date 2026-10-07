package com.team.blog.post.web.dto;

import com.team.blog.post.application.SaveResult;
import java.time.Instant;

/** 저장 응답 (contracts {@code SaveResponse}). */
public record SaveResponse(long version, Instant savedAt) {

    public static SaveResponse from(SaveResult result) {
        return new SaveResponse(result.version(), result.savedAt());
    }
}
