package com.team.blog.post.web.dto;

import com.team.blog.post.application.WorkingCopy;
import java.time.Instant;
import java.util.List;

/** 에디터 열기·새 글 응답 (contracts {@code WorkingCopy}). */
public record WorkingCopyResponse(
        long postId,
        String status,
        boolean editing,
        String title,
        String contentMd,
        long version,
        Instant savedAt,
        String visibility,
        List<String> tags,
        String url) {

    public static WorkingCopyResponse from(WorkingCopy w) {
        return new WorkingCopyResponse(
                w.postId(),
                w.status().name(),
                w.editing(),
                w.title(),
                w.contentMd(),
                w.version(),
                w.savedAt(),
                w.visibility().name(),
                w.tags(),
                w.url());
    }
}
