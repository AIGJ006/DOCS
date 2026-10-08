package com.team.blog.interaction.web;

import com.team.blog.interaction.application.CommentService;
import com.team.blog.interaction.application.CommentView;
import com.team.blog.shared.security.CurrentUser;
import com.team.blog.shared.security.LoginRequired;
import com.team.blog.shared.security.Viewer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 댓글 고치기·지우기 (007 contracts {@code editComment}·{@code deleteComment}). 남의 댓글은 글 작성자·관리자도 404다.
 * 계정 상태는 Service가 본다 — 수정은 {@code CONTENT_WRITE}, 삭제는 {@code CONTENT_CLEANUP}(이메일 인증 전도 자기 댓글은
 * 지운다).
 */
@RestController
public class CommentCommandController {

    private final CommentService commands;

    public CommentCommandController(CommentService commands) {
        this.commands = commands;
    }

    /** 요청 본문. */
    public record EditCommentRequest(String content) {}

    @LoginRequired
    @PatchMapping("/api/comments/{commentId}")
    public CommentView edit(
            @PathVariable String commentId,
            @CurrentUser Long me,
            Viewer viewer,
            @RequestBody EditCommentRequest request) {
        return commands.edit(CommentController.parseId(commentId), me, request.content(), viewer);
    }

    @LoginRequired
    @DeleteMapping("/api/comments/{commentId}")
    public ResponseEntity<Void> delete(@PathVariable String commentId, @CurrentUser Long me) {
        commands.delete(CommentController.parseId(commentId), me);
        return ResponseEntity.noContent().build();
    }
}
