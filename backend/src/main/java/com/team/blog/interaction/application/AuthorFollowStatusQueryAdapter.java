package com.team.blog.interaction.application;

import com.team.blog.post.application.port.AuthorFollowStatusQuery;
import org.springframework.stereotype.Component;

/**
 * 글 상세의 "내가 이 작성자를 팔로우 중인지" (010 T019, research R8). 005가 두었던 기본 구현(항상 {@code false})을 넘겨받아 {@code
 * follow} PK를 {@code EXISTS}로 한 번 본다. 비회원·작성자 본인에게는 상세가 부르지 않는다.
 *
 * <p>(구현 메모) 005 {@code PostReadingPorts}의 {@code @ConditionalOnMissingBean} 기본 Bean은 지웠다 — 008
 * 태그·009 좋아요 때와 같은 이유(일반 설정 클래스의 조건 판정이 컴포넌트 스캔 순서에 따라 흔들릴 수 있음)이고, 세 포트가 모두 소유 기능을 가져 그 설정 클래스도
 * 없앴다.
 */
@Component
public class AuthorFollowStatusQueryAdapter implements AuthorFollowStatusQuery {

    private final FollowQueryService follows;

    public AuthorFollowStatusQueryAdapter(FollowQueryService follows) {
        this.follows = follows;
    }

    @Override
    public boolean isFollowing(long followerId, long followeeId) {
        return follows.isFollowing(followerId, followeeId);
    }
}
