package com.team.blog.post.infra;

import com.team.blog.post.domain.ListCondition;
import com.team.blog.post.domain.VisibilityRegistry;
import com.team.blog.post.domain.VisibilityRule;
import com.team.blog.shared.security.Viewer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 공용 목록 조건 (06 R-2·R-2a·R-2b, FR-009, research R-04). 홈·블로그·블로그 글 수·태그·검색·sitemap·피드 등 "남에게 보이는 글
 * 목록"은 모두 이 조건 하나만 쓴다 — 목록마다 WHERE를 직접 쓰지 않는다.
 *
 * <p><b>별칭 계약</b>: {@code p} = {@code post}, {@code m} = 작성자 {@code member}({@code JOIN member m ON
 * m.id = p.author_id}). 공통 결과는
 *
 * <pre>{@code
 * p.status = 'PUBLISHED' AND p.visibility = 'PUBLIC' AND p.deleted_at IS NULL AND p.hidden_at IS NULL
 *   AND m.withdrawn_at IS NULL [AND p.author_id = :authorId]
 * }</pre>
 *
 * 이고, 앞의 네 조건은 부분 인덱스 {@code ix_post_feed}·{@code ix_post_blog}의 술어와 순서·문구가 같다(인덱스를 타게 하려면 하나도 빼거나
 * 바꾸지 않는다). 블로그 주인이 봐도 조건이 같다 — 자기 비공개·숨김 글도 블로그 목록에 나오지 않는다(06 V-8, H1).
 *
 * <p>{@code m.withdrawn_at} 때문에 {@code member}를 JOIN한다(plan Complexity Tracking의 원칙 II 예외 — 이 클래스와
 * {@link PostQueryRepository}에만 둔다).
 */
@Component
public class VisibilityFilter {

    private final VisibilityRegistry registry;

    public VisibilityFilter(VisibilityRegistry registry) {
        this.registry = registry;
    }

    /**
     * @param viewer 보는 사람 (공통 규칙에서는 조건에 영향이 없다. {@code FRIENDS} 적용자는 친구 조건에 쓴다)
     * @param authorId 블로그 목록이면 블로그 주인, 전체 목록이면 {@code null}
     */
    public SqlCondition forViewer(Viewer viewer, Long authorId) {
        List<String> visibilityParts = new ArrayList<>();
        Map<String, Object> params = new LinkedHashMap<>();
        for (VisibilityRule rule : registry.rules()) {
            ListCondition condition = rule.listCondition(viewer, authorId);
            if (!condition.isExcluded()) {
                visibilityParts.add(condition.sql());
                params.putAll(condition.params());
            }
        }
        String visibility =
                switch (visibilityParts.size()) {
                    case 0 -> "FALSE";
                    case 1 -> visibilityParts.get(0);
                    default -> "(" + String.join(" OR ", visibilityParts) + ")";
                };
        StringBuilder sql =
                new StringBuilder("p.status = 'PUBLISHED' AND ")
                        .append(visibility)
                        .append(" AND p.deleted_at IS NULL AND p.hidden_at IS NULL")
                        .append(" AND m.withdrawn_at IS NULL");
        if (authorId != null) {
            sql.append(" AND p.author_id = :authorId");
            params.put("authorId", authorId);
        }
        return new SqlCondition(sql.toString(), params);
    }

    /**
     * 친구가 보는 블로그 목록 조건 (선택 구현 {@code FRIENDS}, 004 T071, FR-048, research R-14). 전체 공개 글 + 보는 사람이
     * 수락된 친구일 때의 친구 공개 글. 정렬·커서는 {@code k = (p.published_at, p.id)}이고({@code ORDER BY
     * p.published_at DESC, p.id DESC} — 친구 공개 글은 최초 공개 일자가 없다) 부분 인덱스 {@code ix_post_blog_friends}의
     * 술어({@code visibility IN ('PUBLIC','FRIENDS')})를 함께 둔다. 블로그 글 수도 같은 조건의 {@code COUNT(*)}다.
     *
     * <p>홈·태그·검색·sitemap 등 공용 목록은 {@link #forViewer}를 그대로 쓴다 — 친구 공개 글은 거기에 나오지 않는다. 친구 공개를 끈 환경이나
     * 비회원이면 {@link #forViewer}와 같은 글만 남는다(친구 공개 글은 DB에 없거나 EXISTS가 거짓).
     */
    public SqlCondition forFriendBlog(Viewer viewer, long authorId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("authorId", authorId);
        params.put("viewerId", viewer.isAuthenticated() ? viewer.id() : -1L);
        String sql =
                "p.status = 'PUBLISHED' AND p.visibility IN ('PUBLIC', 'FRIENDS')"
                        + " AND p.deleted_at IS NULL AND p.hidden_at IS NULL"
                        + " AND m.withdrawn_at IS NULL AND p.author_id = :authorId"
                        + " AND (p.visibility = 'PUBLIC' OR EXISTS (SELECT 1 FROM friendship f"
                        + " WHERE f.member_a_id = LEAST(p.author_id, :viewerId)"
                        + " AND f.member_b_id = GREATEST(p.author_id, :viewerId)"
                        + " AND f.status = 'ACCEPTED'))";
        return new SqlCondition(sql, params);
    }
}
