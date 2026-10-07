package com.team.blog.post.web;

import com.team.blog.post.application.EditorQueryService;
import com.team.blog.post.application.PostCommandService;
import com.team.blog.post.application.WorkingCopy;
import com.team.blog.post.web.dto.CreatePostRequest;
import com.team.blog.post.web.dto.SaveRequest;
import com.team.blog.post.web.dto.SaveResponse;
import com.team.blog.post.web.dto.WorkingCopyResponse;
import com.team.blog.shared.security.CurrentUser;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 새 글·에디터 열기·수동 저장 (contracts {@code createPost}·{@code getWorkingCopy}·{@code saveWorkingCopy}).
 * 현재 사용자는 세션에서만 꺼낸다.
 */
@RestController
public class PostEditorController {

    private final PostCommandService commands;
    private final EditorQueryService editorQuery;

    public PostEditorController(PostCommandService commands, EditorQueryService editorQuery) {
        this.commands = commands;
        this.editorQuery = editorQuery;
    }

    @PostMapping("/api/posts")
    public ResponseEntity<WorkingCopyResponse> create(
            @CurrentUser Long memberId, @RequestBody(required = false) CreatePostRequest request) {
        CreatePostRequest body = request == null ? new CreatePostRequest(null, null) : request;
        WorkingCopy created = commands.create(memberId, body.title(), body.contentMd());
        return ResponseEntity.created(
                        URI.create("/api/posts/" + created.postId() + "/working-copy"))
                .body(WorkingCopyResponse.from(created));
    }

    /** 수동 저장 — 즉시 DB 반영 (contracts {@code saveWorkingCopy}). */
    @PutMapping("/api/posts/{postId}/working-copy")
    public SaveResponse save(
            @CurrentUser Long memberId,
            @PathVariable long postId,
            @RequestBody SaveRequest request) {
        return SaveResponse.from(
                commands.save(
                        postId,
                        memberId,
                        request.title(),
                        request.contentMd(),
                        request.baseVersionOrZero()));
    }

    @GetMapping("/api/posts/{postId}/working-copy")
    public WorkingCopyResponse workingCopy(@CurrentUser Long memberId, @PathVariable long postId) {
        return WorkingCopyResponse.from(editorQuery.open(postId, memberId));
    }
}
