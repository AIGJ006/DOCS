package com.team.blog.interaction.infra;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 좋아요 행 ({@code post_like}, interaction 모듈 소유, 009 research R1·R11). PK {@code (post_id,
 * member_id)}가 1인 1글 1건을 지킨다 — 동시에 같은 쌍을 넣으면 두 번째는 PK 대기 뒤 0행이 된다. 카운터({@code post.like_count})는
 * 여기서 바꾸지 않는다(post 모듈 {@code PostCounterService}).
 */
@Repository
public class LikeRepository {

    private final JdbcClient jdbc;

    public LikeRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** 없으면 넣는다. @return 실제로 넣었으면 1, 이미 있었으면 0 */
    public int insertIfAbsent(long postId, long memberId) {
        return jdbc.sql(
                        "INSERT INTO post_like (post_id, member_id) VALUES (:p, :m)"
                                + " ON CONFLICT DO NOTHING")
                .param("p", postId)
                .param("m", memberId)
                .update();
    }

    /** 있으면 지운다. @return 실제로 지웠으면 1, 없었으면 0 */
    public int deleteIfPresent(long postId, long memberId) {
        return jdbc.sql("DELETE FROM post_like WHERE post_id = :p AND member_id = :m")
                .param("p", postId)
                .param("m", memberId)
                .update();
    }

    /** 그 회원이 그 글에 좋아요했는가 (PK 조회 1번). */
    public boolean exists(long postId, long memberId) {
        return jdbc.sql(
                        "SELECT EXISTS (SELECT 1 FROM post_like WHERE post_id = :p AND member_id = :m)")
                .param("p", postId)
                .param("m", memberId)
                .query(Boolean.class)
                .single();
    }

    /**
     * 그 회원의 좋아요를 모두 지운다 (탈퇴 정리, {@code ix_post_like_member}).
     *
     * @return 지운 행의 글 번호 (행마다 하나 — 한 회원은 한 글에 한 행뿐이라 중복 없음)
     */
    public List<Long> deleteAllByMember(long memberId) {
        return jdbc.sql("DELETE FROM post_like WHERE member_id = :m RETURNING post_id")
                .param("m", memberId)
                .query(Long.class)
                .list();
    }
}
