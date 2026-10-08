package com.team.blog.moderation.application;

import com.team.blog.interaction.application.CommentQueryService;
import com.team.blog.post.application.spi.PostPurgeStep;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 완전 삭제 전 대기 신고 사건 종료 (006 T061 임시 구현, research R11, 13 §2-5 0단계, FR-033). <b>014-report-hide plan이
 * 패키지·소유를 확정한 뒤 이전한다.</b>
 *
 * <p>그 글 대상 사건과 그 글 댓글(답글 포함) 대상 사건 중 {@code PENDING}만 {@code CLOSED_NO_TARGET}으로 닫는다({@code
 * handled_at = now()}, {@code handled_by = NULL}). 이미 처리된 사건은 그대로 둔다. {@code post_id}·{@code
 * comment_id}는 이어지는 {@code DELETE FROM post}의 FK {@code SET NULL}이 비운다. 신고({@code report}) 행은 남긴다.
 * 이벤트는 발행하지 않는다.
 */
@Component
public class ReportPostPurgeStep implements PostPurgeStep {

    static final int ORDER = 10;
    private static final int CHUNK = 1000;
    private static final String CLOSE =
            "UPDATE report_case SET status = 'CLOSED_NO_TARGET', handled_at = now(),"
                    + " handled_by = NULL WHERE status = 'PENDING' AND ";

    private final CommentQueryService comments;
    private final NamedParameterJdbcTemplate jdbc;

    public ReportPostPurgeStep(CommentQueryService comments, NamedParameterJdbcTemplate jdbc) {
        this.comments = comments;
        this.jdbc = jdbc;
    }

    @Override
    public int order() {
        return ORDER;
    }

    @Override
    public void beforePurge(long postId) {
        jdbc.update(CLOSE + "post_id = :postId", Map.of("postId", postId));
        List<Long> commentIds = comments.commentIdsOfPost(postId);
        // 바인드 인자 수 한계(32767)를 넘지 않게 나눠 보낸다
        for (int from = 0; from < commentIds.size(); from += CHUNK) {
            List<Long> chunk = commentIds.subList(from, Math.min(from + CHUNK, commentIds.size()));
            jdbc.update(CLOSE + "comment_id IN (:commentIds)", Map.of("commentIds", chunk));
        }
    }
}
