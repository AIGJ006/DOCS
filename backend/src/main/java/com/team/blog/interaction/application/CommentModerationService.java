package com.team.blog.interaction.application;

import com.team.blog.account.application.MemberDisplay;
import com.team.blog.account.application.MemberQueryService;
import com.team.blog.interaction.infra.CommentRepository;
import com.team.blog.interaction.infra.CommentRow;
import com.team.blog.post.application.PostCounterService;
import com.team.blog.shared.error.NotFoundException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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
 * boolean hide(long commentId, long adminId, String reason, Instant now);
 * boolean unhide(long commentId);
 * Optional<CommentSnapshot> snapshot(long commentId);
 * Map<Long, Boolean> hiddenOf(Collection<Long> commentIds);
 * }</pre>
 */
@Service
public class CommentModerationService {

    private final CommentRepository comments;
    private final PostCounterService counters;
    private final MemberQueryService members;

    public CommentModerationService(
            CommentRepository comments, PostCounterService counters, MemberQueryService members) {
        this.comments = comments;
        this.counters = counters;
        this.members = members;
    }

    /**
     * 신고 접수·처리 화면이 읽는 댓글 상태와 내용 (014 data-model §3 — 필드는 014가 정함, 014 T006).
     *
     * @param content 삭제된 자리면 빈 값
     * @param deleted "삭제된 자리"인가
     * @param hidden 관리자 숨김인가
     * @param authorWithdrawn 작성자가 탈퇴 유예이거나 익명 처리됐는가
     */
    public record CommentSnapshot(
            long commentId,
            long postId,
            long authorId,
            String content,
            boolean deleted,
            boolean hidden,
            boolean authorWithdrawn) {}

    /**
     * 숨김. 이미 숨김이면 아무것도 안 함(멱등). 없는 댓글·삭제된 자리면 {@link NotFoundException}. 정상 → 댓글 수 −1.
     *
     * @return 이번에 새로 숨겼으면 true (014가 {@code ContentHidden}을 낼지 정한다)
     */
    @Transactional
    public boolean hide(long commentId, long adminId, String reason, Instant now) {
        CommentRow row = lockAlive(commentId);
        if (row.isHidden()) {
            return false;
        }
        comments.hide(commentId, adminId, reason, now.truncatedTo(ChronoUnit.MICROS));
        counters.adjustCommentCount(row.postId(), -1);
        return true;
    }

    /**
     * 해제. 숨김이 아니면 아무것도 안 함. 숨김 → 댓글 수 +1. 없는 댓글·삭제된 자리면 {@link NotFoundException}.
     *
     * @return 이번에 해제했으면 true
     */
    @Transactional
    public boolean unhide(long commentId) {
        CommentRow row = lockAlive(commentId);
        if (!row.isHidden()) {
            return false;
        }
        comments.unhide(commentId);
        counters.adjustCommentCount(row.postId(), 1);
        return true;
    }

    /** 없는 댓글이면 빈 값. 작성자 탈퇴 여부는 001 {@code findDisplays}로 읽는다(SQL 2번). */
    @Transactional(readOnly = true)
    public Optional<CommentSnapshot> snapshot(long commentId) {
        return comments.find(commentId)
                .map(
                        row -> {
                            Map<Long, MemberDisplay> authors =
                                    members.findDisplays(List.of(row.authorId()));
                            MemberDisplay author = authors.get(row.authorId());
                            return new CommentSnapshot(
                                    row.id(),
                                    row.postId(),
                                    row.authorId(),
                                    row.isDeleted() ? "" : row.content(),
                                    row.isDeleted(),
                                    row.isHidden(),
                                    author == null || author.withdrawn());
                        });
    }

    /** 댓글마다 지금 숨김인가 (SQL 1번, 014 처리됨 목록의 [숨김 해제] 표시). 없는 댓글은 결과에 없다. */
    @Transactional(readOnly = true)
    public Map<Long, Boolean> hiddenOf(Collection<Long> commentIds) {
        return comments.hiddenOf(commentIds);
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
