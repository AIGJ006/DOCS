package com.team.blog.post.application;

import com.team.blog.post.domain.PostNotFoundException;
import com.team.blog.post.domain.PostStatus;
import com.team.blog.post.domain.Visibility;
import com.team.blog.post.infra.PostModerationRepository;
import com.team.blog.post.infra.PostQueryRepository;
import com.team.blog.post.infra.PostView;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 글 숨김·해제·스냅샷 공개 Service (014 T011, contracts/moderation-sql.md §6, research R2). 014 moderation이
 * 부른다 — 숨김 권한·사건·이벤트는 014 몫이고, 이 Service는 {@code post.hidden_*} 세 칸만 바꾼다. 좋아요·댓글 수·{@code
 * first_public_at}· {@code updated_at}은 그대로라 해제하면 숨기기 전과 같다(FR-024·FR-027, SC-006). 작성자의 수정·다시
 * 발행·공개 범위 변경·휴지통· 복구는 {@code hidden_*}를 건드리지 않는다(FR-023).
 */
@Service
public class PostModerationService {

    /** 스냅샷 원문 앞부분 기본 길이 (014 {@code blog.moderation.snapshot-content-chars} 기본값). */
    public static final int DEFAULT_SNAPSHOT_CHARS = 2000;

    /** 처리 화면의 "현재: …" 상태 (014 research R7 표, 판정 순서대로). 014 {@code TargetState}의 글 값과 이름이 같다. */
    public enum State {
        GONE,
        AUTHOR_WITHDRAWN,
        HIDDEN,
        TRASHED,
        PRIVATE,
        PUBLIC
    }

    private final PostModerationRepository repository;
    private final PostQueryRepository posts;

    public PostModerationService(PostModerationRepository repository, PostQueryRepository posts) {
        this.repository = repository;
        this.posts = posts;
    }

    /** 글이 있으면(휴지통 포함) 제목 + 원문 앞 2,000자. */
    @Transactional(readOnly = true)
    public Optional<PostSnapshot> snapshot(long postId) {
        return snapshot(postId, DEFAULT_SNAPSHOT_CHARS);
    }

    /** 글이 있으면(휴지통 포함) 제목 + 원문 앞 {@code maxChars} 코드 포인트. */
    @Transactional(readOnly = true)
    public Optional<PostSnapshot> snapshot(long postId, int maxChars) {
        return repository
                .findSnapshot(postId, maxChars)
                .map(
                        row ->
                                new PostSnapshot(
                                        row.id(),
                                        row.authorId(),
                                        row.title(),
                                        row.head(),
                                        row.hidden(),
                                        row.trashed()));
    }

    /**
     * 숨긴다(행 {@code FOR UPDATE}). 휴지통 글도 숨길 수 있다. 이미 숨김이면 아무것도 바꾸지 않고 {@code false}(멱등).
     *
     * @throws PostNotFoundException 글이 없으면
     */
    @Transactional
    public boolean hide(long postId, long adminId, String reason, Instant now) {
        boolean hidden =
                repository
                        .lockHidden(postId)
                        .orElseThrow(() -> new PostNotFoundException("글 숨김: 없음"));
        if (hidden) {
            return false;
        }
        repository.hide(postId, adminId, reason, now.truncatedTo(ChronoUnit.MICROS));
        return true;
    }

    /**
     * 숨김을 푼다(행 {@code FOR UPDATE}). 숨김이 아니면 {@code false}.
     *
     * @throws PostNotFoundException 글이 없으면
     */
    @Transactional
    public boolean unhide(long postId) {
        boolean hidden =
                repository
                        .lockHidden(postId)
                        .orElseThrow(() -> new PostNotFoundException("글 숨김 해제: 없음"));
        if (!hidden) {
            return false;
        }
        repository.unhide(postId);
        return true;
    }

    /** 지금 상태 (research R7 표). 비공개·임시는 {@link State#PRIVATE}. */
    @Transactional(readOnly = true)
    public State currentState(long postId) {
        Optional<PostView> view = posts.findPostView(postId);
        if (view.isEmpty()) {
            return State.GONE;
        }
        PostView post = view.get();
        if (post.isAuthorWithdrawn()) {
            return State.AUTHOR_WITHDRAWN;
        }
        if (post.isHidden()) {
            return State.HIDDEN;
        }
        if (post.isDeleted()) {
            return State.TRASHED;
        }
        if (post.status() != PostStatus.PUBLISHED || post.visibility() != Visibility.PUBLIC) {
            return State.PRIVATE;
        }
        return State.PUBLIC;
    }

    /** 글마다 지금 숨김인가 (SQL 1번). 없는 글은 결과에 없다. */
    @Transactional(readOnly = true)
    public Map<Long, Boolean> hiddenOf(Collection<Long> postIds) {
        return repository.hiddenOf(postIds);
    }
}
