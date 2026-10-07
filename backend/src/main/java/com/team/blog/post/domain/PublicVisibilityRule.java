package com.team.blog.post.domain;

import com.team.blog.post.infra.PostView;
import com.team.blog.shared.security.Viewer;
import org.springframework.stereotype.Component;

/** 전체 공개: 누구나 보고, 공개 목록에 들어간다 (06 §1). */
@Component
public class PublicVisibilityRule implements VisibilityRule {

    /** 부분 인덱스 {@code ix_post_feed}·{@code ix_post_blog}의 술어와 같은 문구 (06 R-2b). */
    static final ListCondition CONDITION = ListCondition.of("p.visibility = 'PUBLIC'");

    @Override
    public Visibility visibility() {
        return Visibility.PUBLIC;
    }

    @Override
    public boolean canRead(PostView post, Viewer viewer) {
        return true;
    }

    @Override
    public ListCondition listCondition(Viewer viewer, Long authorId) {
        return CONDITION;
    }
}
