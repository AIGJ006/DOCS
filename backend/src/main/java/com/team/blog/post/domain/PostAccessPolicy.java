package com.team.blog.post.domain;

import com.team.blog.post.infra.PostView;
import com.team.blog.shared.security.Viewer;
import org.springframework.stereotype.Component;

/**
 * 글 읽기 판정 한 곳 (06 §7 R-1, research R-03, FR-010). 상세·댓글·좋아요·미리보기 등 "이 글을 볼 수 있나"는 모두 이것으로 판단한다. 목록은
 * 같은 규칙을 SQL로 옮긴 {@code VisibilityFilter}를 쓴다.
 *
 * <ol>
 *   <li>휴지통({@code deleted_at})이면 작성자를 포함해 누구도 볼 수 없다 — 삭제 여부를 가장 먼저 본다(13 §2-6 #3).
 *   <li>작성자 본인이면 임시·비공개·숨김 글도 본다(관리자도 자기 글이면 작성자).
 *   <li>그 밖에는 발행됨·숨김 아님·작성자 탈퇴 유예 아님이고, 그 공개 범위의 {@link VisibilityRule}이 참일 때만.
 * </ol>
 *
 * 관리자라는 이유로 남의 비공개·숨김 글을 보지 못한다(FR-005, 42 P-11). 탈퇴 유예 작성자 본인의 요청은 001 {@code
 * WithdrawnAccountGateFilter}가 먼저 403으로 막는다.
 */
@Component
public class PostAccessPolicy {

    private final VisibilityRegistry registry;

    public PostAccessPolicy(VisibilityRegistry registry) {
        this.registry = registry;
    }

    public boolean canRead(PostView post, Viewer viewer) {
        if (post.isDeleted()) {
            return false;
        }
        if (viewer.isAuthorOf(post.authorId())) {
            return true;
        }
        return post.status() == PostStatus.PUBLISHED
                && !post.isHidden()
                && !post.isAuthorWithdrawn()
                && registry.rule(post.visibility()).canRead(post, viewer);
    }
}
