package com.team.blog.post.infra;

import com.team.blog.post.domain.FriendshipChecker;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link FriendshipChecker}의 읽기 전용 구현 (004 US8). 001 소유 {@code friendship} 표를 PK로 한 번 읽기만 한다(쓰지 않음,
 * plan Complexity Tracking의 원칙 II 예외와 같은 이유). 친구 공개를 켰을 때만 등록된다.
 */
@Repository
@ConditionalOnProperty(name = "blog.visibility.friends.enabled", havingValue = "true")
public class JdbcFriendshipChecker implements FriendshipChecker {

    private final JdbcClient jdbc;

    public JdbcFriendshipChecker(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean areFriends(long memberId, long otherMemberId) {
        if (memberId == otherMemberId) {
            return false;
        }
        return jdbc.sql(
                        """
                        SELECT EXISTS (
                            SELECT 1 FROM friendship
                             WHERE member_a_id = ? AND member_b_id = ? AND status = 'ACCEPTED')
                        """)
                .param(Math.min(memberId, otherMemberId))
                .param(Math.max(memberId, otherMemberId))
                .query(Boolean.class)
                .single();
    }
}
