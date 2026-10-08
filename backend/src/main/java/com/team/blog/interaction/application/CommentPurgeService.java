package com.team.blog.interaction.application;

import com.team.blog.interaction.infra.CommentRepository;
import com.team.blog.post.application.PostCounterService;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴 30일 정리의 댓글 단계 (007 T048, contracts/events.md §2-2, 21 §11 SQL 2-a~2-d, FR-039).
 *
 * <p>015 {@code CommentWithdrawalPurgeStep}({@code WithdrawalPurgeStep} order 20 — 그 회원의 글을 지우는 order 10
 * 다음)이 자기 단계 트랜잭션 안에서 부른다({@code MANDATORY}). 단계 클래스는 015 tasks가 만든다. 015가 부를 서명:
 *
 * <pre>{@code
 * PurgeResult purgeByAuthor(long memberId);
 * }</pre>
 *
 * 이벤트는 내지 않는다(알림·신고 정리는 015의 다른 단계). 남의 답글의 {@code reply_to_member_id}는 그대로 두고, 회원이 익명 처리되면 {@code
 * replyTo = {withdrawn: true}}로 보인다.
 */
@Service
public class CommentPurgeService {

    private static final Logger log = LoggerFactory.getLogger(CommentPurgeService.class);

    private final CommentRepository comments;
    private final PostCounterService counters;
    private final Clock clock;

    public CommentPurgeService(
            CommentRepository comments, PostCounterService counters, Clock clock) {
        this.comments = comments;
        this.counters = counters;
        this.clock = clock;
    }

    /**
     * 결과 (로그용).
     *
     * @param deleted 지운 행 수 (빈 자리 정리 포함)
     * @param placeholders 자리로 남긴 내 최상위 수
     */
    public record PurgeResult(int deleted, int placeholders) {}

    /** 그 회원의 댓글을 정리한다 (2-a 글별 감소 → 2-b 자리 → 2-c 나머지 삭제 → 2-d 빈 자리 정리). */
    @Transactional(propagation = Propagation.MANDATORY)
    public PurgeResult purgeByAuthor(long memberId) {
        Map<Long, Integer> deltas = new LinkedHashMap<>();
        comments.countNormalByPost(memberId).forEach(c -> deltas.put(c.postId(), -c.count()));
        counters.adjustCommentCounts(deltas);
        int placeholders =
                comments.placeholderRootsWithOthersReplies(
                        memberId, clock.instant().truncatedTo(ChronoUnit.MICROS));
        List<Long> parents = comments.deleteRestOf(memberId);
        int emptied = comments.deleteEmptyPlaceholders(memberId, parents);
        PurgeResult result = new PurgeResult(parents.size() + emptied, placeholders);
        log.info(
                "탈퇴 댓글 정리: memberId={} deleted={} placeholders={}",
                memberId,
                result.deleted(),
                result.placeholders());
        return result;
    }
}
