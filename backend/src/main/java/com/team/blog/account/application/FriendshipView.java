package com.team.blog.account.application;

import com.team.blog.account.domain.LastActiveView;

/**
 * 나와 그 회원의 관계 (openapi {@code FriendshipView}).
 *
 * @param otherId 상대 회원 번호(응답에는 나가지 않는다)
 * @param lastActive 친구 + 둘 다 공개 + 값 있음일 때만, 아니면 null(응답에서 키를 뺀다)
 */
public record FriendshipView(FriendshipState status, long otherId, LastActiveView lastActive) {

    public FriendshipView(FriendshipState status, long otherId) {
        this(status, otherId, null);
    }
}
