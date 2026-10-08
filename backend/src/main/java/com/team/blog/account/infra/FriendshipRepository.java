package com.team.blog.account.infra;

import com.team.blog.account.domain.Friendship;
import com.team.blog.account.domain.FriendshipId;
import com.team.blog.account.domain.FriendshipStatus;
import java.sql.Timestamp;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@code friendship} 쓰기·한 행 조회 (data-model §2-5). 요청·맞요청 수락·변화 없음을 {@code INSERT … ON CONFLICT} 한
 * 문장으로 해서 동시 맞요청도 행 하나로 끝난다(US7 #3).
 */
@Repository
public class FriendshipRepository {

    /** 요청 결과. {@code inserted}면 새 요청, 아니면 PENDING → ACCEPTED. */
    public record UpsertResult(FriendshipStatus status, boolean inserted) {}

    private final JdbcClient jdbc;

    public FriendshipRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** 요청하거나(행 없음) 상대가 보낸 요청을 수락한다. 이미 친구·내가 보낸 요청이면 빈 값(변화 없음). */
    public Optional<UpsertResult> requestOrAccept(long me, long other) {
        FriendshipId id = FriendshipId.of(me, other);
        return jdbc.sql(
                        """
                        INSERT INTO friendship (member_a_id, member_b_id, requested_by)
                        VALUES (:a, :b, :me)
                        ON CONFLICT (member_a_id, member_b_id) DO UPDATE
                           SET status = 'ACCEPTED', accepted_at = CURRENT_TIMESTAMP
                         WHERE friendship.status = 'PENDING' AND friendship.requested_by <> :me
                        RETURNING status, (xmax = 0) AS inserted
                        """)
                .param("a", id.memberAId())
                .param("b", id.memberBId())
                .param("me", me)
                .query(
                        (rs, n) ->
                                new UpsertResult(
                                        FriendshipStatus.valueOf(rs.getString("status")),
                                        rs.getBoolean("inserted")))
                .optional();
    }

    public Optional<Friendship> find(long one, long other) {
        FriendshipId id = FriendshipId.of(one, other);
        return jdbc.sql(
                        """
                        SELECT requested_by, status, created_at, accepted_at FROM friendship
                         WHERE member_a_id = ? AND member_b_id = ?
                        """)
                .params(id.memberAId(), id.memberBId())
                .query(
                        (rs, n) -> {
                            Timestamp accepted = rs.getTimestamp("accepted_at");
                            return new Friendship(
                                    id,
                                    rs.getLong("requested_by"),
                                    FriendshipStatus.valueOf(rs.getString("status")),
                                    rs.getTimestamp("created_at").toInstant(),
                                    accepted == null ? null : accepted.toInstant());
                        })
                .optional();
    }

    /** 관계를 지운다(거절·취소·끊기). 지운 행 수. */
    public int delete(long one, long other) {
        FriendshipId id = FriendshipId.of(one, other);
        return jdbc.sql("DELETE FROM friendship WHERE member_a_id = ? AND member_b_id = ?")
                .params(id.memberAId(), id.memberBId())
                .update();
    }
}
