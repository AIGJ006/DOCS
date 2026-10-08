package com.team.blog.account.application;

import com.team.blog.account.infra.FriendshipRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 친구 여부 공개 조회 (001 T129). 004 친구 공개(선택 기능)와 US8 최근 활동 표시가 쓴다. 한 행 조회. */
@Service
@Transactional(readOnly = true)
public class FriendshipQueryService {

    private final FriendshipRepository friendships;

    public FriendshipQueryService(FriendshipRepository friendships) {
        this.friendships = friendships;
    }

    /** 두 회원이 서로 친구({@code ACCEPTED})인가. 같은 회원이면 false. */
    public boolean areFriends(long a, long b) {
        return a != b && friendships.find(a, b).map(f -> f.isAccepted()).orElse(false);
    }
}
