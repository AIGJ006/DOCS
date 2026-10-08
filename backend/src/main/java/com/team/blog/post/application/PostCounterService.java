package com.team.blog.post.application;

import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 글 카운터 공개 Service (007 T009, research R5). 다른 모듈(interaction)은 {@code post} 테이블을 직접 고치지 않고 이것으로
 * 카운터를 바꾼다(원칙 II). 부르는 쪽의 트랜잭션 안에서만 쓸 수 있다({@code MANDATORY}) — 댓글 행 변경과 카운터가 같은 트랜잭션에서
 * 바뀐다(05 J-2, SC-002).
 *
 * <p>값이 0 아래로 가면 {@code ck_post_counts} 위반으로 트랜잭션 전체가 실패한다(일관성 깨짐을 숨기지 않는다). 009가 {@code
 * adjustLikeCount}를 이 클래스에 더한다.
 */
@Service
public class PostCounterService {

    private final JdbcClient jdbc;

    public PostCounterService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** 그 글의 댓글 수를 {@code delta}만큼 바꾼다. 0이면 아무것도 하지 않는다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void adjustCommentCount(long postId, int delta) {
        if (delta == 0) {
            return;
        }
        jdbc.sql("UPDATE post SET comment_count = comment_count + :delta WHERE id = :id")
                .param("delta", delta)
                .param("id", postId)
                .update();
    }

    /** 여러 글의 댓글 수를 한 번에(SQL 1번) 바꾼다 — 탈퇴 정리용. 0인 항목은 뺀다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void adjustCommentCounts(Map<Long, Integer> deltas) {
        Map<Long, Integer> changes =
                deltas.entrySet().stream()
                        .filter(e -> e.getValue() != null && e.getValue() != 0)
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        if (changes.isEmpty()) {
            return;
        }
        StringBuilder ids = new StringBuilder("{");
        StringBuilder ns = new StringBuilder("{");
        changes.forEach(
                (id, n) -> {
                    if (ids.length() > 1) {
                        ids.append(',');
                        ns.append(',');
                    }
                    ids.append(id);
                    ns.append(n);
                });
        jdbc.sql(
                        """
                        UPDATE post p SET comment_count = p.comment_count + d.n
                          FROM unnest(CAST(:ids AS bigint[]), CAST(:ns AS int[])) AS d(id, n)
                         WHERE p.id = d.id
                        """)
                .param("ids", ids.append('}').toString())
                .param("ns", ns.append('}').toString())
                .update();
    }
}
