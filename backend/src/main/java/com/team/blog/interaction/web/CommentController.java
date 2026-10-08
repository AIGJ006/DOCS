package com.team.blog.interaction.web;

import com.team.blog.interaction.application.CommentPage;
import com.team.blog.interaction.application.CommentQueryService;
import com.team.blog.interaction.application.CommentService;
import com.team.blog.interaction.application.CommentView;
import com.team.blog.interaction.application.ReplyPage;
import com.team.blog.shared.error.NotFoundException;
import com.team.blog.shared.security.CurrentUser;
import com.team.blog.shared.security.LoginRequired;
import com.team.blog.shared.security.Viewer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 댓글 목록·답글 펼치기·작성 (007 contracts {@code listComments}·{@code listReplies}·{@code createComment}).
 *
 * <ul>
 *   <li>경로 변수는 {@code String} — 숫자가 아니면 400이 아니라 404(005 상세와 같음).
 *   <li>보는 사람·현재 사용자는 세션에서만 온다. 요청 본문에 회원 번호가 없다.
 *   <li>목록 {@code Cache-Control}은 그 글 상태로({@code CacheControlPolicy.forPost}) — 공개가 아닌 글은 {@code
 *       private, no-store}.
 * </ul>
 */
@RestController
public class CommentController {

    private final CommentQueryService queries;
    private final CommentService commands;

    public CommentController(CommentQueryService queries, CommentService commands) {
        this.queries = queries;
        this.commands = commands;
    }

    @GetMapping("/api/posts/{postId}/comments")
    public ResponseEntity<CommentPage> list(
            @PathVariable String postId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String around,
            Viewer viewer) {
        CommentQueryService.Result<CommentPage> result =
                queries.page(parseId(postId), cursor, around, viewer);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, result.cacheControl())
                .body(result.body());
    }

    @GetMapping("/api/comments/{rootId}/replies")
    public ResponseEntity<ReplyPage> replies(
            @PathVariable String rootId,
            @RequestParam(required = false) String cursor,
            Viewer viewer) {
        CommentQueryService.Result<ReplyPage> result =
                queries.replies(parseId(rootId), cursor, viewer);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, result.cacheControl())
                .body(result.body());
    }

    /** 요청 본문. 회원 번호 칸은 없다. */
    public record CreateCommentRequest(String content, Long replyToCommentId) {}

    /** 201 새 댓글, 200 10초 안 같은 요청(처음 댓글). */
    @LoginRequired
    @PostMapping("/api/posts/{postId}/comments")
    public ResponseEntity<CommentView> create(
            @PathVariable String postId,
            @CurrentUser Long me,
            Viewer viewer,
            @RequestBody CreateCommentRequest request) {
        long id = parseId(postId);
        CommentService.CreateResult result =
                commands.create(id, me, request.content(), request.replyToCommentId(), viewer);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(result.comment());
    }

    /** 숫자가 아니거나 1 미만이면 404. */
    static long parseId(String raw) {
        if (raw == null || raw.isEmpty() || raw.length() > 18) {
            throw new NotFoundException("댓글: 번호 형식");
        }
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c < '0' || c > '9') {
                throw new NotFoundException("댓글: 번호 형식");
            }
        }
        long id = Long.parseLong(raw);
        if (id < 1) {
            throw new NotFoundException("댓글: 번호 범위");
        }
        return id;
    }
}
