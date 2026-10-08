package com.team.blog.post.application;

import com.team.blog.post.application.spi.PostPurgeStep;
import com.team.blog.post.infra.RedisAutosaveStore;
import com.team.blog.post.infra.TrashPostRepository;
import com.team.blog.shared.event.PostPurged;
import com.team.blog.shared.infra.redis.RedisGuard;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 완전 삭제 공용 루틴 (006 T011·T063, research R10·R22·R25, 13 §2-5). 영구 삭제·30일 휴지통 비우기·빈 임시글 즉시 삭제·015 탈퇴
 * 정리가 함께 쓴다.
 *
 * <ol>
 *   <li>{@link PostPurgeStep}을 {@code order()} 오름차순으로 부른다(신고 사건 종료 10 → 사진 연결 해제 20). 그 시점에 {@code
 *       post} 행은 아직 있다.
 *   <li>{@code DELETE FROM post} — 딸린 행은 FK CASCADE·SET NULL이 처리한다.
 *   <li>{@code notify}이면 {@link PostPurged}를 발행한다(구독자는 커밋 후 처리).
 *   <li>커밋 후 Redis 자동 저장 보관분({@code autosave:post:{id}}·{@code autosave:dirty})을 버전과 상관없이 지운다.
 *       Redis 장애는 경고 로그만 남긴다(constitution V).
 * </ol>
 *
 * <p>호출한 쪽 트랜잭션 안에서만 쓴다({@code MANDATORY}). 트랜잭션 안에서 외부 호출(파일 삭제 등)을 하지 않는다 — 사진 파일은 003 정리 배치가 나중에
 * 지운다.
 */
@Service
public class PostPurgeService {

    private static final Logger log = LoggerFactory.getLogger(PostPurgeService.class);

    /** FK 위반으로 보는 SQLSTATE (01 결정 기록 2026-10-06). */
    private static final Set<String> FK_VIOLATIONS = Set.of("23001", "23503");

    private final List<PostPurgeStep> steps;
    private final TrashPostRepository trashPosts;
    private final RedisAutosaveStore autosaves;
    private final ApplicationEventPublisher events;

    public PostPurgeService(
            List<PostPurgeStep> steps,
            TrashPostRepository trashPosts,
            RedisAutosaveStore autosaves,
            ApplicationEventPublisher events) {
        this.steps = steps.stream().sorted(Comparator.comparingInt(PostPurgeStep::order)).toList();
        this.trashPosts = trashPosts;
        this.autosaves = autosaves;
        this.events = events;
    }

    /**
     * 글 하나를 완전히 지운다. 소유·상태 판정은 호출한 쪽이 행을 잠근 뒤에 이미 했어야 한다.
     *
     * @param notify {@link PostPurged}를 발행할지 (빈 임시글이면 {@code false})
     * @throws PostPurgeFkViolationException 예상 밖 FK 위반 (트랜잭션은 롤백된다)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void purge(long postId, long authorId, boolean notify) {
        for (PostPurgeStep step : steps) {
            step.beforePurge(postId);
        }
        try {
            trashPosts.deleteById(postId);
        } catch (DataIntegrityViolationException e) {
            if (isFkViolation(e)) {
                log.error("완전 삭제 중 FK 위반: postId={}", postId);
                throw new PostPurgeFkViolationException(postId, e);
            }
            throw e;
        }
        if (notify) {
            events.publishEvent(new PostPurged(postId, authorId));
        }
        log.info("완전 삭제: postId={} authorId={} notify={}", postId, authorId, notify);
        RedisGuard.runAfterCommit(() -> autosaves.delete(postId));
    }

    /**
     * 그 회원의 글을 휴지통 포함 모두 완전히 지운다 (015 {@code PostWithdrawalPurgeStep} order 10이 부른다, research R25).
     * 글마다 {@link PostPurged}를 한 번씩 발행한다.
     *
     * @return 지운 글 수
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int purgeAllByAuthor(long authorId) {
        List<Long> ids = trashPosts.findIdsByAuthorIncludingTrashed(authorId);
        for (long id : ids) {
            purge(id, authorId, true);
        }
        log.info("회원 글 완전 삭제: authorId={} count={}", authorId, ids.size());
        return ids.size();
    }

    static boolean isFkViolation(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && FK_VIOLATIONS.contains(sql.getSQLState())) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }
}
