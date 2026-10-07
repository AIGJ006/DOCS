package com.team.blog.post.domain;

import com.team.blog.post.infra.PostView;
import com.team.blog.shared.security.Viewer;

/**
 * 공개 범위 값 하나의 규칙 (06 §7 R-3, research R-03). 값마다 Bean을 하나 두고, 등록된 Bean의 {@link #visibility()} 집합이 곧
 * 허용값 집합이다({@link VisibilityRegistry}). 공통 Bean은 {@link PublicVisibilityRule}·{@link
 * PrivateVisibilityRule}이고, {@code FRIENDS} 적용자는 {@code FriendsVisibilityRule}을 더한다(공통 코드를 고치지 않음).
 *
 * <p>작성자 본인 예외와 휴지통·숨김·작성자 탈퇴 유예는 {@link PostAccessPolicy}가 먼저 처리하므로 규칙은 "작성자가 아닌 사람이 발행된 글을 볼 수
 * 있나"만 판단한다.
 */
public interface VisibilityRule {

    /** 이 규칙이 맡는 공개 범위 값. */
    Visibility visibility();

    /** 작성자가 아닌 사람이 이 공개 범위의 발행 글을 볼 수 있나. */
    boolean canRead(PostView post, Viewer viewer);

    /**
     * 공개 목록에 이 공개 범위 글을 넣는 조건. 넣지 않으면 {@link ListCondition#excluded()}.
     *
     * @param authorId 블로그 목록이면 블로그 주인, 전체 목록이면 {@code null}
     */
    ListCondition listCondition(Viewer viewer, Long authorId);
}
