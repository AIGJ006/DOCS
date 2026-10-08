package com.team.blog.moderation.application;

import com.team.blog.interaction.application.CommentModerationService;
import com.team.blog.interaction.application.CommentModerationService.CommentSnapshot;
import com.team.blog.moderation.domain.ReportTarget;
import com.team.blog.post.application.PostModerationService;
import com.team.blog.post.application.PostReadService;
import com.team.blog.post.application.PostSnapshot;
import com.team.blog.post.domain.PostNotFoundException;
import com.team.blog.shared.error.NotFoundException;
import com.team.blog.shared.event.ReportTargetType;
import com.team.blog.shared.security.Viewer;
import org.springframework.stereotype.Component;

/**
 * 신고·직접 숨김 대상 판정 (014 T020, research R2·R3 ④). 볼 수 없는 대상은 이유를 가리지 않고 같은 404다.
 *
 * <ul>
 *   <li>글: 004 {@link PostReadService#requireReadable} + 스냅샷(제목, 원문 앞 2,000자). 작성자는 자기 비공개·임시·숨김 글도
 *       보므로 여기를 지나 "자기 것" 400을 받는다.
 *   <li>댓글: 007 {@link CommentModerationService#snapshot} + 그 글을 같은 판정으로 + 댓글이 정상(삭제된 자리·작성자 탈퇴 유예는
 *       404, 숨김은 작성자 본인이 아니면 404).
 * </ul>
 */
@Component
public class ReportTargetResolver {

    private final PostReadService postReadService;
    private final PostModerationService postModeration;
    private final CommentModerationService commentModeration;

    public ReportTargetResolver(
            PostReadService postReadService,
            PostModerationService postModeration,
            CommentModerationService commentModeration) {
        this.postReadService = postReadService;
        this.postModeration = postModeration;
        this.commentModeration = commentModeration;
    }

    /**
     * @param snapshotChars 글 원문 앞부분 길이
     * @throws NotFoundException 없거나 이 사람이 볼 수 없으면
     */
    public ReportTarget resolve(
            ReportTargetType type, long targetId, Viewer viewer, int snapshotChars) {
        return type == ReportTargetType.POST
                ? post(targetId, viewer, snapshotChars)
                : comment(targetId, viewer);
    }

    private ReportTarget post(long postId, Viewer viewer, int snapshotChars) {
        postReadService.requireReadable(postId, viewer);
        PostSnapshot snapshot =
                postModeration
                        .snapshot(postId, snapshotChars)
                        .orElseThrow(() -> new PostNotFoundException("신고 대상: 글 사라짐"));
        return new ReportTarget(
                ReportTargetType.POST,
                postId,
                postId,
                snapshot.authorId(),
                snapshot.title(),
                snapshot.contentHead());
    }

    private ReportTarget comment(long commentId, Viewer viewer) {
        CommentSnapshot snapshot =
                commentModeration
                        .snapshot(commentId)
                        .orElseThrow(() -> new NotFoundException("신고 대상: 댓글 없음"));
        postReadService.requireReadable(snapshot.postId(), viewer);
        if (snapshot.deleted() || snapshot.authorWithdrawn()) {
            throw new NotFoundException("신고 대상: 삭제·탈퇴 댓글");
        }
        if (snapshot.hidden() && !viewer.isAuthorOf(snapshot.authorId())) {
            throw new NotFoundException("신고 대상: 숨긴 댓글");
        }
        return new ReportTarget(
                ReportTargetType.COMMENT,
                commentId,
                snapshot.postId(),
                snapshot.authorId(),
                null,
                snapshot.content());
    }
}
