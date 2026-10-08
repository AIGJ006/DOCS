package com.team.blog.interaction.application;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 댓글 조회 공개 Service (006 T058 최소 구현, research R11). <b>007-comments가 확장·소유한다</b> — 지금은 006 신고 종료
 * 단계({@code ReportPostPurgeStep})가 쓰는 {@link #commentIdsOfPost(long)} 하나뿐이다. 007이 댓글 엔티티·저장소를 만들면 이
 * 메서드를 그 저장소로 옮긴다.
 */
@Service
public class CommentQueryService {

    private final NamedParameterJdbcTemplate jdbc;

    public CommentQueryService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 그 글의 댓글 번호 전부 (답글·삭제 표시된 댓글 포함, 번호 순). 댓글이 없으면 빈 목록. */
    @Transactional(readOnly = true)
    public List<Long> commentIdsOfPost(long postId) {
        return jdbc.queryForList(
                "SELECT id FROM comment WHERE post_id = :postId ORDER BY id",
                Map.of("postId", postId),
                Long.class);
    }
}
