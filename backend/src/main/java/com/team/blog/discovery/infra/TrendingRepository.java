package com.team.blog.discovery.infra;

import com.team.blog.discovery.application.trending.TrendingProperties;
import com.team.blog.post.infra.SqlCondition;
import com.team.blog.post.infra.VisibilityFilter;
import com.team.blog.shared.security.Viewer;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 트렌딩 계산 SQL 1번 (012 T026, research R3, contracts §1, FR-003~008).
 *
 * <p>후보 = 공용 조건(004 {@link VisibilityFilter}, 비회원 기준) ∧ {@code first_public_at > now −
 * window}({@code ix_post_feed} 범위). 글마다 남의 댓글 작성자 수(작성자 제외, 삭제·숨김 제외, 같은 사람 1명 — Clarifications
 * Q1)를 상관 서브쿼리로 세고({@code uq_comment_post_id (post_id, id)}), 좋아요 또는 남의 댓글이 있는 글만 점수를 매긴다. 작성자당
 * {@code per-author}개({@code row_number}), 점수 → 최초 공개 최신 → 번호 큰 순, 상위 {@code limit}개의 번호만 돌려준다.
 *
 * <p><b>원칙 II 읽기 예외 (plan Complexity Tracking 1행)</b>: 점수·작성자당 3개·상위 100개를 한 문장에서 정하려고 {@code
 * comment}를 읽기 전용으로 읽는다(007 공개 Service로 받으면 SQL 2번 + 메모리 정렬).
 */
@Repository
public class TrendingRepository {

    private final JdbcClient jdbc;
    private final VisibilityFilter visibilityFilter;
    private final TrendingProperties properties;

    public TrendingRepository(
            JdbcClient jdbc, VisibilityFilter visibilityFilter, TrendingProperties properties) {
        this.jdbc = jdbc;
        this.visibilityFilter = visibilityFilter;
        this.properties = properties;
    }

    /**
     * @param now 기준 시각 (경과 시간·7일 범위)
     * @param limit 스냅샷 100, 즉시 계산 대체 9
     * @return 순위 순 글 번호
     */
    public List<Long> compute(Instant now, int limit) {
        SqlCondition visibility = visibilityFilter.forViewer(Viewer.anonymous(), null);
        Map<String, Object> params = new LinkedHashMap<>(visibility.params());
        OffsetDateTime at = now.atOffset(ZoneOffset.UTC);
        params.put("now", at);
        params.put("since", at.minus(properties.window()));
        params.put("wLike", properties.weightLike());
        params.put("wComment", properties.weightCommenter());
        params.put("wView", properties.weightView());
        params.put("offsetHours", properties.offsetHours());
        params.put("gravity", properties.gravity());
        params.put("perAuthor", properties.perAuthor());
        params.put("limit", limit);
        String sql =
                """
                WITH candidate AS (
                  SELECT p.id, p.author_id, p.like_count, p.view_count, p.first_public_at,
                         (SELECT count(DISTINCT c.author_id) FROM comment c
                           WHERE c.post_id = p.id AND c.author_id <> p.author_id
                             AND c.deleted_at IS NULL AND c.hidden_at IS NULL) AS commenters
                    FROM post p
                    JOIN member m ON m.id = p.author_id
                   WHERE %s
                     AND p.first_public_at > :since
                ), scored AS (
                  SELECT *, (:wLike * like_count + :wComment * commenters + :wView * view_count)
                            / power(greatest(extract(epoch FROM (CAST(:now AS timestamptz) - first_public_at)), 0)
                                    / 3600.0 + :offsetHours, :gravity) AS score
                    FROM candidate
                   WHERE like_count > 0 OR commenters > 0
                ), ranked AS (
                  SELECT *, row_number() OVER (PARTITION BY author_id
                                               ORDER BY score DESC, first_public_at DESC, id DESC) AS rn
                    FROM scored
                )
                SELECT id FROM ranked
                 WHERE rn <= :perAuthor
                 ORDER BY score DESC, first_public_at DESC, id DESC
                 LIMIT :limit
                """
                        .formatted(visibility.sql());
        return jdbc.sql(sql).params(params).query(Long.class).list();
    }
}
