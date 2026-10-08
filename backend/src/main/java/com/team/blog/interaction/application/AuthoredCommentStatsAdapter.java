package com.team.blog.interaction.application;

import com.team.blog.account.application.port.AuthoredCommentStats;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 015 탈퇴 안내의 "남의 글에 쓴 댓글 수" (015 T022, research R7, {@code ix_comment_author}). 내 글에 쓴 댓글은 글과 함께
 * 지워지므로 세지 않고, 이미 지운 댓글도 세지 않는다. 숨김 댓글은 센다(정리 대상).
 */
@Component
public class AuthoredCommentStatsAdapter implements AuthoredCommentStats {

    private final JdbcClient jdbc;

    public AuthoredCommentStatsAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public long countOnOthersPosts(long memberId) {
        return jdbc.sql(
                        """
                        SELECT count(*) FROM comment c JOIN post p ON p.id = c.post_id
                         WHERE c.author_id = :m AND p.author_id <> :m AND c.deleted_at IS NULL
                        """)
                .param("m", memberId)
                .query(Long.class)
                .single();
    }
}
