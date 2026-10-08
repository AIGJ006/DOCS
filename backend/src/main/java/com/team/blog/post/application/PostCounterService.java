package com.team.blog.post.application;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 글 카운터 공개 Service (007 T009, research R5). 다른 모듈(interaction)은 {@code post} 테이블을 직접 고치지 않고 이것으로
 * 카운터를 바꾼다(원칙 II). 부르는 쪽의 트랜잭션 안에서만 쓸 수 있다({@code MANDATORY}) — 댓글 행 변경과 카운터가 같은 트랜잭션에서 바뀐다(05 J-2,
 * SC-002).
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

    // ---- 009 좋아요·조회수 (contracts/view-pipeline.md §7, research R2·R9) ----

    /**
     * 그 글의 좋아요 수를 {@code delta}만큼 바꾼다 (좋아요가 실제로 생기거나 지워진 트랜잭션 안에서, 009 R1). 0이면 아무것도 하지 않는다. 행 잠금으로
     * 같은 글의 동시 변경이 한 번에 하나씩 반영된다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void adjustLikeCount(long postId, int delta) {
        if (delta == 0) {
            return;
        }
        jdbc.sql("UPDATE post SET like_count = like_count + :delta WHERE id = :id")
                .param("delta", delta)
                .param("id", postId)
                .update();
    }

    /** 지금 좋아요 수 (같은 트랜잭션에서 읽으면 방금 바꾼 값). 없는 글은 0. */
    @Transactional(readOnly = true)
    public int likeCount(long postId) {
        return jdbc.sql("SELECT like_count FROM post WHERE id = :id")
                .param("id", postId)
                .query(Integer.class)
                .optional()
                .orElse(0);
    }

    /**
     * 조회수를 {@code n}만큼 더한다 (009 1분 반영, FR-030 — {@code updated_at}은 건드리지 않는다).
     *
     * @return 글이 있으면 {@code true}, 완전 삭제돼 없으면 {@code false}
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean addViews(long postId, long n) {
        if (n <= 0) {
            return jdbc.sql("SELECT EXISTS (SELECT 1 FROM post WHERE id = :id)")
                    .param("id", postId)
                    .query(Boolean.class)
                    .single();
        }
        return jdbc.sql("UPDATE post SET view_count = view_count + :n WHERE id = :id")
                        .param("n", n)
                        .param("id", postId)
                        .update()
                > 0;
    }

    /** 여러 글의 좋아요 수를 한 번에(SQL 1번) 바꾼다 — 009 탈퇴 정리용. 0인 항목은 뺀다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void adjustLikeCounts(Map<Long, Integer> deltas) {
        Map<Long, Integer> changes =
                deltas.entrySet().stream()
                        .filter(e -> e.getValue() != null && e.getValue() != 0)
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        if (changes.isEmpty()) {
            return;
        }
        Long[] ids = changes.keySet().toArray(Long[]::new);
        Integer[] ns = changes.values().toArray(Integer[]::new);
        jdbc.sql(
                        """
                        UPDATE post p SET like_count = p.like_count + d.n
                          FROM unnest(CAST(:ids AS bigint[]), CAST(:ns AS int[])) AS d(id, n)
                         WHERE p.id = d.id
                        """)
                .param("ids", arrayLiteral(ids))
                .param("ns", arrayLiteral(ns))
                .update();
    }

    /**
     * 좋아요 수가 실제 건수와 다른 글만 고친다 (009 보정 배치, 30 §4-1). 정상이면 빈 목록.
     *
     * @return 고친 글 번호 (번호 순)
     */
    @Transactional
    public List<Long> reconcileLikeCounts() {
        return jdbc
                .sql(
                        """
                        UPDATE post p SET like_count = c.n
                          FROM (SELECT p2.id, count(l.post_id) AS n
                                  FROM post p2 LEFT JOIN post_like l ON l.post_id = p2.id
                                 GROUP BY p2.id) c
                         WHERE p.id = c.id AND p.like_count <> c.n
                        RETURNING p.id
                        """)
                .query(Long.class)
                .list()
                .stream()
                .sorted()
                .toList();
    }

    private static String arrayLiteral(Object[] values) {
        StringBuilder out = new StringBuilder("{");
        for (Object v : values) {
            if (out.length() > 1) {
                out.append(',');
            }
            out.append(v);
        }
        return out.append('}').toString();
    }
}
