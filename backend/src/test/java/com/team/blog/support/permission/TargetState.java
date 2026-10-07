package com.team.blog.support.permission;

import com.team.blog.support.fixture.PostFixtures;

/** 권한 매트릭스의 대상 상태 (004 T021의 7개 글 상태 + 없는 글 + 대상 없음). */
public enum TargetState {
    PUBLISHED_PUBLIC(PostFixtures.State.PUBLISHED_PUBLIC),
    PUBLISHED_PRIVATE(PostFixtures.State.PUBLISHED_PRIVATE),
    EDITING(PostFixtures.State.EDITING),
    DRAFT(PostFixtures.State.DRAFT),
    TRASHED(PostFixtures.State.TRASHED),
    HIDDEN(PostFixtures.State.HIDDEN),
    AUTHOR_WITHDRAWN(PostFixtures.State.AUTHOR_WITHDRAWN),
    /** 존재하지 않는 글 번호. */
    NONEXISTENT(null),
    /** 대상 글이 없는 행동 (새 글 만들기 등). 행동에는 {@code postId = null}이 넘어간다. */
    NONE(null);

    private final PostFixtures.State fixture;

    TargetState(PostFixtures.State fixture) {
        this.fixture = fixture;
    }

    /** 만들 글 상태. 없는 글·대상 없음은 {@code null}. */
    public PostFixtures.State fixture() {
        return fixture;
    }
}
