package com.team.blog.interaction.application;

import com.team.blog.interaction.infra.CommentRepository;
import com.team.blog.interaction.infra.CommentRow;
import com.team.blog.post.application.PostCounterService;
import com.team.blog.shared.error.NotFoundException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 댓글 숨김·해제 공개 Service (007 T048, contracts/events.md §2-1). 014 신고·숨김이 부른다 — 숨김 권한·사유 코드·{@code
 * ContentHidden} 발행은 014 몫이고, 댓글 수 일관성(숨김 −1, 해제 +1)은 이 기능이 책임진다(SC-002).
 *
 * <p>각 메서드는 자기 트랜잭션({@code REQUIRED})에서 그 행을 {@code FOR UPDATE}로 잠근다.
 *
 * <pre>{@code
 * void hide(long commentId, long adminId, String reason, Instant now);
 * void unhide(long commentId);
 * Optional<CommentSnapshot> snapshot(long commentId);
 * }</pre>
 */
@Service
public class CommentModerationService {

    private final CommentRepository comments;
    private final PostCounterService counters;

    public CommentModerationService(CommentRepository comments, PostCounterService counters) {
        this.comments = comments;
        this.counters = counters;
    }

    /**
     * 신고 접수 때 복사할 내용 (014).
     *
     * @param content 삭제된 자리면 빈 값
     */
    public record CommentSnapshot(
            long commentId, long postId, long authorId, String content, Instant createdAt) {}

    /** 숨김. 이미 숨김이면 아무것도 안 함(멱등). 없는 댓글·삭제된 자리면 {@link NotFoundException}. 정상 → 댓글 수 −1. */
    @Transactional
    public void hide(long commentId, long adminId, String reason, Instant now) {
        CommentRow row = lockAlive(commentId);
        if (row.isHidden()) {
            return;
        }
        comments.hide(commentId, adminId, reason, now.truncatedTo(ChronoUnit.MICROS));
        counters.adjustCommentCount(row.postId(), -1);
    }

    /** 해제. 숨김이 아니면 아무것도 안 함. 숨김 → 댓글 수 +1. 없는 댓글·삭제된 자리면 {@link NotFoundException}. */
    @Transactional
    public void unhide(long commentId) {
        CommentRow row = lockAlive(commentId);
        if (!row.isHidden()) {
            return;
        }
        comments.unhide(commentId);
        counters.adjustCommentCount(row.postId(), 1);
    }

    /** 없는 댓글이면 빈 값. */
    @Transactional(readOnly = true)
    public Optional<CommentSnapshot> snapshot(long commentId) {
        return comments.find(commentId)
                .map(
                        row ->
                                new CommentSnapshot(
                                        row.id(),
                                        row.postId(),
                                        row.authorId(),
                                        row.isDeleted() ? "" : row.content(),
                                        row.createdAt()));
    }

    private CommentRow lockAlive(long commentId) {
        CommentRow row =
                comments.findForUpdate(commentId)
                        .orElseThrow(() -> new NotFoundException("댓글 숨김: 없음"));
        if (row.isDeleted()) {
            throw new NotFoundException("댓글 숨김: 삭제된 자리");
        }
        return row;
    }
}
