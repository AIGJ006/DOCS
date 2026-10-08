package com.team.blog.post.domain;

import com.team.blog.post.infra.PostView;
import com.team.blog.shared.security.Viewer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 친구 공개 (선택 구현, 004 T070, US8, FR-048, research R-14). {@code
 * blog.visibility.friends.enabled=true}({@code friends} 프로필)일 때만 등록된다 — 등록되면 {@link
 * VisibilityRegistry}의 허용값에 {@code FRIENDS}가 들어간다.
 *
 * <ul>
 *   <li>상세: 작성자(먼저 {@link PostAccessPolicy}가 처리)와 수락된 친구만. 비회원·친구 아님·요청 중은 false(404).
 *   <li>목록: 홈·태그·검색·sitemap 등 공용 목록에는 넣지 않는다({@link ListCondition#excluded()}). 친구가 보는 블로그 목록·글 수는
 *       {@code VisibilityFilter.forFriendBlog}가 따로 만든다.
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "blog.visibility.friends.enabled", havingValue = "true")
public class FriendsVisibilityRule implements VisibilityRule {

    private final FriendshipChecker friendships;

    public FriendsVisibilityRule(FriendshipChecker friendships) {
        this.friendships = friendships;
    }

    @Override
    public Visibility visibility() {
        return Visibility.FRIENDS;
    }

    @Override
    public boolean canRead(PostView post, Viewer viewer) {
        if (!viewer.isAuthenticated()) {
            return false;
        }
        return friendships.areFriends(post.authorId(), viewer.id());
    }

    @Override
    public ListCondition listCondition(Viewer viewer, Long authorId) {
        return ListCondition.excluded();
    }
}
