package com.team.blog.moderation.application;

import com.team.blog.interaction.application.CommentQueryService;
import com.team.blog.moderation.infra.ReportCaseRepository;
import com.team.blog.post.application.spi.PostPurgeStep;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 글 완전 삭제 전 대기 신고 사건 종료 (006 {@link PostPurgeStep} order 10, 014 research R8,
 * contracts/moderation-sql.md §5 첫 줄, 13 §2-5 0단계, FR-032).
 *
 * <p>그 글 대상 사건과 그 글 댓글(답글 포함) 대상 사건 중 {@code PENDING}만 {@code CLOSED_NO_TARGET}으로 닫는다({@code
 * handled_at = now}, {@code handled_by = NULL}). 이미 처리된 사건은 그대로 둔다. {@code post_id}·{@code
 * comment_id}는 이어지는 {@code DELETE FROM post}의 FK {@code SET NULL}이 비운다. 사건·신고·스냅샷은 남는다. 이벤트는 발행하지
 * 않는다(FR-030). 006 완전 삭제 트랜잭션 안에서 불린다.
 */
@Component
public class ReportPostPurgeStep implements PostPurgeStep {

    static final int ORDER = 10;
    private static final int CHUNK = 1000;

    private final CommentQueryService comments;
    private final ReportCaseRepository cases;
    private final Clock clock;

    public ReportPostPurgeStep(
            CommentQueryService comments, ReportCaseRepository cases, Clock clock) {
        this.comments = comments;
        this.cases = cases;
        this.clock = clock;
    }

    @Override
    public int order() {
        return ORDER;
    }

    @Override
    public void beforePurge(long postId) {
        Instant now = clock.instant();
        cases.closeNoTargetForPost(postId, now);
        List<Long> commentIds = comments.commentIdsOfPost(postId);
        // 바인드 인자 수 한계(32767)를 넘지 않게 나눠 보낸다
        for (int from = 0; from < commentIds.size(); from += CHUNK) {
            cases.closeNoTargetForComments(
                    commentIds.subList(from, Math.min(from + CHUNK, commentIds.size())), now);
        }
    }
}
