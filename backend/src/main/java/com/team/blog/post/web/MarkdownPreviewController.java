package com.team.blog.post.web;

import com.team.blog.post.application.MarkdownPreviewService;
import com.team.blog.post.web.dto.PreviewRequest;
import com.team.blog.post.web.dto.PreviewResponse;
import com.team.blog.shared.security.CurrentUser;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 서버 렌더러 미리보기 (contracts {@code previewMarkdown}). */
@RestController
public class MarkdownPreviewController {

    private final MarkdownPreviewService previewService;

    public MarkdownPreviewController(MarkdownPreviewService previewService) {
        this.previewService = previewService;
    }

    @PostMapping("/api/markdown/preview")
    public PreviewResponse preview(
            @CurrentUser Long memberId, @RequestBody PreviewRequest request) {
        return new PreviewResponse(previewService.preview(memberId, request.contentMd()));
    }
}
