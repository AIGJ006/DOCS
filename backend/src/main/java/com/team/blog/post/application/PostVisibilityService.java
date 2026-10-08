package com.team.blog.post.application;

import com.team.blog.post.domain.Post;
import com.team.blog.post.domain.PostNotFoundException;
import com.team.blog.post.domain.Visibility;
import com.team.blog.post.domain.VisibilityChange;
import com.team.blog.post.domain.VisibilityRegistry;
import com.team.blog.post.infra.PostRepository;
import com.team.blog.shared.error.ApiException;
import com.team.blog.shared.error.CommonReasonCode;
import com.team.blog.shared.event.PostVisibilityChanged;
import com.team.blog.shared.event.PostWentPublic;
import com.team.blog.shared.security.AccountStatusGuard;
import com.team.blog.shared.security.ActionKind;
import com.team.blog.shared.security.Viewer;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공개 범위 지정 (004 T034, {@code PUT /api/posts/{postId}/visibility}, FR-016~FR-021, research
 * R-05·R-10·R-11·R-21·R-25).
 *
 * <p>판정 순서(42 §3, 처음 걸린 단계의 응답):
 *
 * <ol>
 *   <li>① 비회원 → 401 {@code LOGIN_REQUIRED}
 *   <li>② 계정 상태 — 001 {@link AccountStatusGuard}({@link ActionKind#CONTENT_WRITE}): 인증 전 403 {@code
 *       EMAIL_NOT_VERIFIED}, 정지 403 {@code ACCOUNT_SUSPENDED}, 탈퇴 유예 403 {@code ACCOUNT_WITHDRAWN}.
 *       대상 조회보다 먼저라 글의 존재가 드러나지 않는다
 *   <li>③④ 내 글을 행 잠금으로 읽는다({@code author_id = 현재 사용자 AND deleted_at IS NULL}). 없음·남의 글·휴지통 글은 같은
 *       404. 관리자 예외 없음(FR-035)
 *   <li>⑤ 값 검사({@link VisibilityRegistry#require}) → 400 {@code INVALID_VISIBILITY}. 남의 글에 잘못된 값을
 *       보내도 404가 먼저다
 * </ol>
 *
 * 값이 실제로 바뀐 발행 글이면 같은 트랜잭션에서 {@link PostVisibilityChanged}를, 이번에 처음 공개됐으면 {@link PostWentPublic}도
 * 발행한다(커밋 후 전달, 롤백되면 버려짐). 임시글은 값만 저장하고 이벤트가 없다(R-25). 같은 글에 대한 동시 요청은 행 잠금으로 한 번에 하나씩 반영된다.
 *
 * <p>현재 사용자는 세션에서 만든 {@link Viewer}로만 받는다 — 요청 본문의 회원 번호는 쓰지 않는다(FR-026).
 */
@Service
public class PostVisibilityService {

    private static final Logger log = LoggerFactory.getLogger(PostVisibilityService.class);

    /** 변경 결과 (응답 {@code {visibility, firstPublicAt}}). */
    public record VisibilityChangeResult(Visibility visibility, Instant firstPublicAt) {}

    private final AccountStatusGuard accountStatusGuard;
    private final PostRepository posts;
    private final VisibilityRegistry registry;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public PostVisibilityService(
            AccountStatusGuard accountStatusGuard,
            PostRepository posts,
            VisibilityRegistry registry,
            ApplicationEventPublisher events,
            Clock clock) {
        this.accountStatusGuard = accountStatusGuard;
        this.posts = posts;
        this.registry = registry;
        this.events = events;
        this.clock = clock;
    }

    /**
     * @param postId 글 번호 (1 미만이면 없는 글과 같은 404)
     * @param rawVisibility 요청 본문의 값 그대로 (검사는 ⑤단계)
     */
    @Transactional
    public VisibilityChangeResult change(Viewer viewer, long postId, String rawVisibility) {
        if (viewer == null || !viewer.isAuthenticated()) {
            throw new ApiException(CommonReasonCode.LOGIN_REQUIRED);
        }
        long me = viewer.id();
        accountStatusGuard.requireActive(me, ActionKind.CONTENT_WRITE);
        Post post =
                posts.findForUpdateByIdAndAuthorId(postId, me)
                        .orElseThrow(() -> new PostNotFoundException("공개 범위 변경: 내 글 아님"));
        Visibility to = registry.require(rawVisibility, "visibility");

        Instant now = PostCommandService.now(clock);
        VisibilityChange change = post.changeVisibility(to, now);
        if (change.changed() && post.isPublished()) {
            events.publishEvent(new PostVisibilityChanged(postId, me, change.from(), to, now));
            if (change.wentPublic()) {
                events.publishEvent(new PostWentPublic(postId, me, post.firstPublicAt()));
            }
        }
        log.info(
                "공개 범위 변경: postId={} authorId={} {}→{} changed={} wentPublic={}",
                postId,
                me,
                change.from(),
                to,
                change.changed(),
                change.wentPublic());
        return new VisibilityChangeResult(post.visibility(), post.firstPublicAt());
    }
}
