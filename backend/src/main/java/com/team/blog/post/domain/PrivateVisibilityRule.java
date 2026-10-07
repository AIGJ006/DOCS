package com.team.blog.post.domain;

import com.team.blog.post.infra.PostView;
import com.team.blog.shared.security.Viewer;
import org.springframework.stereotype.Component;

/**
 * 나만 보기: 작성자 외에는 아무도 못 보고(관리자 포함, FR-005), 어떤 목록에도 넣지 않는다 — 작성자 본인의 블로그 목록에도(06 V-8). 작성자 본인 예외는
 * {@link PostAccessPolicy}가 먼저 처리한다.
 */
@Component
public class PrivateVisibilityRule implements VisibilityRule {

    @Override
    public Visibility visibility() {
        return Visibility.PRIVATE;
    }

    @Override
    public boolean canRead(PostView post, Viewer viewer) {
        return false;
    }

    @Override
    public ListCondition listCondition(Viewer viewer, Long authorId) {
        return ListCondition.excluded();
    }
}
