package com.team.blog.post.application;

import com.team.blog.post.domain.PostAccessPolicy;
import com.team.blog.post.domain.PostNotFoundException;
import com.team.blog.post.domain.PostStatus;
import com.team.blog.post.infra.PostQueryRepository;
import com.team.blog.post.infra.PostView;
import com.team.blog.shared.security.Viewer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다른 모듈용 읽기 판정 (42 §3 ③, FR-011). 007 댓글·009 좋아요·005 상세 등이 "이 글을 볼 수 있나"를 이것으로 확인한다.
 *
 * <p>없음·휴지통·비공개·임시·숨김·탈퇴 유예 작성자를 응답에서 구분하지 않는다 — 모두 같은 {@link PostNotFoundException}(404)이고, 이유는
 * DEBUG 로그에만 남긴다(06 R-4, research R-26).
 */
@Service
@Transactional(readOnly = true)
public class PostReadService {

    private static final Logger log = LoggerFactory.getLogger(PostReadService.class);

    private final PostQueryRepository posts;
    private final PostAccessPolicy policy;

    public PostReadService(PostQueryRepository posts, PostAccessPolicy policy) {
        this.posts = posts;
        this.policy = policy;
    }

    /**
     * @return 볼 수 있는 글의 판정 투영
     * @throws PostNotFoundException 없거나 볼 수 없으면 (이유 구분 없음)
     */
    public PostView requireReadable(long postId, Viewer viewer) {
        PostView post = posts.findPostView(postId).orElse(null);
        if (post == null) {
            log.debug("글 읽기 거부 postId={} 이유=없음", postId);
            throw new PostNotFoundException();
        }
        if (!policy.canRead(post, viewer)) {
            log.debug("글 읽기 거부 postId={} 이유={}", postId, reason(post));
            throw new PostNotFoundException();
        }
        return post;
    }

    /**
     * 그 글을 볼 수 있는가 — {@link #requireReadable}과 같은 판정을 예외 없이 돌려준다 (011 알림 처리 시점 확인, data-model §4).
     * 없는 글은 {@code false}.
     */
    public boolean isReadable(long postId, Viewer viewer) {
        return posts.findPostView(postId).map(post -> policy.canRead(post, viewer)).orElse(false);
    }

    /**
     * 읽기 판정 없이 그 글의 작성자 번호 (007 댓글 삭제 이벤트용 — 내 댓글은 글을 볼 수 없어도 지울 수 있다, 007 research R9). 없는 글은 빈 값.
     */
    public java.util.Optional<Long> findAuthorId(long postId) {
        return posts.findPostView(postId).map(PostView::authorId);
    }

    private static String reason(PostView post) {
        if (post.isDeleted()) {
            return "휴지통";
        }
        if (post.status() != PostStatus.PUBLISHED) {
            return "임시글";
        }
        if (post.isHidden()) {
            return "숨김";
        }
        if (post.isAuthorWithdrawn()) {
            return "작성자 탈퇴 유예";
        }
        return "공개 범위(" + post.visibility() + ")";
    }
}
